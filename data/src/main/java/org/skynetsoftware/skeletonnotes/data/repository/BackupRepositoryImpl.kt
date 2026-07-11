package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.skynetsoftware.skeletonnotes.data.mapper.toJsonString
import org.skynetsoftware.skeletonnotes.data.mapper.toNotes
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * [BackupRepository] backed by [NotesRepository] and [org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage]. Produces and
 * consumes a ZIP archive laid out as:
 *
 * ```
 * notes.json          // array of all notes with attachment metadata
 * attachments/<id>    // raw attachment bytes, filename == attachment id
 * ```
 */
internal class BackupRepositoryImpl(
    private val notesRepository: NotesRepository,
    private val attachmentFileStorage: AttachmentFileStorage,
) : BackupRepository {
    companion object {
        /** ZIP entry containing the exported note manifest. */
        private const val NOTES_ENTRY = "notes.json"

        /** ZIP directory prefix containing raw attachment files. */
        private const val ATTACHMENTS_DIR = "attachments/"
    }

    override suspend fun exportNotes(outputStream: OutputStream): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val notes =
                    when (val result = notesRepository.getAllNotesWithAttachments()) {
                        is Result.Success -> result.data
                        is Result.Failure -> return@withContext Result.Failure(result.throwable)
                    }
                val zip = ZipOutputStream(outputStream)
                zip.putNextEntry(ZipEntry(NOTES_ENTRY))
                zip.write(notes.toJsonString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                notes.flatMap { it.attachments }.forEach { attachment ->
                    val file = attachmentFileStorage.getFile(attachment.id)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry("$ATTACHMENTS_DIR${attachment.id}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                // finish() writes the central directory without closing the caller-owned stream.
                zip.finish()
                Result.Success(notes.size)
            } catch (t: Throwable) {
                Result.Failure(t)
            }
        }

    override suspend fun importNotes(
        inputStream: InputStream,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
    ): Result<ImportSummary> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(importArchive(inputStream, onConflict))
            } catch (t: Throwable) {
                Result.Failure(t)
            }
        }

    /** A planned note import that will be saved after referenced attachments are streamed. */
    private class ImportPlan(
        /** Note to save, already remapped when the conflict resolution requested keep-both. */
        val note: Note,
        /** Attachment import plans that belong to [note]. */
        val attachments: List<AttachmentPlan>,
        /** Counter bucket to increment when [note] saves successfully. */
        val successOutcome: ImportOutcome,
    )

    /** A planned attachment import for one note. */
    private class AttachmentPlan(
        /** Original attachment id used by the backup ZIP entry. */
        val sourceId: String,
        /** Final local attachment id to write. */
        val targetId: String,
        /** Attachment metadata to save after [localPath] is populated. */
        val attachment: Attachment,
    ) {
        /** Local storage path returned after streaming the attachment entry, or `null` if absent. */
        var localPath: String? = null
    }

    /** Result of planning a single incoming note. */
    private sealed interface ImportDecision {
        /** The incoming note should be skipped without saving. */
        data object Skipped : ImportDecision

        /** The incoming note should be saved after ZIP attachments are streamed. */
        data class Planned(
            val plan: ImportPlan,
        ) : ImportDecision
    }

    /** Counter bucket for a planned note import. */
    private enum class ImportOutcome { IMPORTED, OVERWRITTEN, SKIPPED }

    /** Imports a backup ZIP, streaming attachment entries directly into final storage. */
    private suspend fun importArchive(
        inputStream: InputStream,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
    ): ImportSummary {
        val plans = mutableListOf<ImportPlan>()
        val attachmentsBySourceId = mutableMapOf<String, MutableList<AttachmentPlan>>()
        var notesSeen = false
        var skipped = 0

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == NOTES_ENTRY -> {
                        notesSeen = true
                        planNotes(zip.readBytes().toString(Charsets.UTF_8), onConflict).forEach { decision ->
                            when (decision) {
                                ImportDecision.Skipped -> skipped++
                                is ImportDecision.Planned -> {
                                    plans += decision.plan
                                    indexAttachments(decision.plan, attachmentsBySourceId)
                                }
                            }
                        }
                    }
                    name.startsWith(ATTACHMENTS_DIR) && !entry.isDirectory -> {
                        if (!notesSeen) error("Archive has attachments before $NOTES_ENTRY")
                        streamAttachment(name.removePrefix(ATTACHMENTS_DIR), zip, attachmentsBySourceId)
                    }
                }
                entry = zip.nextEntry
            }
        }
        if (!notesSeen) error("Archive is missing $NOTES_ENTRY")
        return savePlans(plans, skipped)
    }

    /** Converts the notes manifest into import decisions, prompting for conflicts as needed. */
    private suspend fun planNotes(
        notesJson: String,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
    ): List<ImportDecision> =
        JSONArray(notesJson).toNotes().map { incoming ->
            planNote(incoming, onConflict)
        }

    /** Returns true when [attachmentId] can only name a file inside attachment storage. */
    private fun isSafeAttachmentId(attachmentId: String): Boolean =
        attachmentId.isNotBlank() &&
            !attachmentId.contains('/') &&
            !attachmentId.contains('\\') &&
            !attachmentId.contains("..")

    /** Plans [incoming], prompting for conflicts through [onConflict] when necessary. */
    private suspend fun planNote(
        incoming: NoteWithAttachments,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
    ): ImportDecision {
        val existing = (notesRepository.getNoteById(incoming.note.id) as? Result.Success)?.data
        val resolution = existing?.let { onConflict(it.note, incoming.note) }
        return when (resolution) {
            ConflictResolution.KEEP_EXISTING -> ImportDecision.Skipped
            ConflictResolution.OVERWRITE ->
                ImportDecision.Planned(createPlan(incoming, remapIds = false, ImportOutcome.OVERWRITTEN))
            ConflictResolution.KEEP_BOTH ->
                ImportDecision.Planned(createPlan(incoming, remapIds = true, ImportOutcome.IMPORTED))
            null ->
                ImportDecision.Planned(createPlan(incoming, remapIds = false, ImportOutcome.IMPORTED))
        }
    }

    /**
     * Builds an [ImportPlan]. When [remapIds] is true the note and every attachment receive fresh ids
     * for keep-both. Unsafe attachment ids are excluded so no path-traversal filename is written.
     */
    private fun createPlan(
        incoming: NoteWithAttachments,
        remapIds: Boolean,
        successOutcome: ImportOutcome,
    ): ImportPlan {
        val noteId = if (remapIds) UUID.randomUUID().toString() else incoming.note.id
        val attachments =
            incoming.attachments.mapNotNull { attachment ->
                if (!isSafeAttachmentId(attachment.id)) return@mapNotNull null
                val newId = if (remapIds) UUID.randomUUID().toString() else attachment.id
                if (!isSafeAttachmentId(newId)) return@mapNotNull null
                AttachmentPlan(
                    sourceId = attachment.id,
                    targetId = newId,
                    attachment = attachment.copy(id = newId, noteId = noteId),
                )
            }
        val note = incoming.note.copy(id = noteId, remoteLastModified = 0L)
        return ImportPlan(note, attachments, successOutcome)
    }

    /** Adds [plan]'s attachment work to [attachmentsBySourceId] for streaming lookup. */
    private fun indexAttachments(
        plan: ImportPlan,
        attachmentsBySourceId: MutableMap<String, MutableList<AttachmentPlan>>,
    ) {
        plan.attachments.forEach { attachment ->
            attachmentsBySourceId.getOrPut(attachment.sourceId) { mutableListOf() }.add(attachment)
        }
    }

    /** Streams one attachment ZIP entry directly into storage when a planned note references it. */
    private fun streamAttachment(
        sourceId: String,
        zip: ZipInputStream,
        attachmentsBySourceId: Map<String, List<AttachmentPlan>>,
    ) {
        if (!isSafeAttachmentId(sourceId)) return
        val targets = attachmentsBySourceId[sourceId].orEmpty().filter { it.localPath == null }
        val target = targets.firstOrNull() ?: return
        val path = attachmentFileStorage.writeStream(target.targetId, zip)
        targets.filter { it.targetId == target.targetId }.forEach { it.localPath = path }
    }

    /** Saves all planned notes in a single batch and returns the final import summary. */
    private suspend fun savePlans(
        plans: List<ImportPlan>,
        initiallySkipped: Int,
    ): ImportSummary {
        val notes =
            plans.map { plan ->
                val attachments =
                    plan.attachments.mapNotNull { attachment ->
                        val path = attachment.localPath ?: return@mapNotNull null
                        attachment.attachment.copy(uri = path)
                    }
                NoteWithAttachments(plan.note, attachments)
            }
        // Import is atomic: the whole batch saves in one transaction, so on failure nothing is
        // persisted and every planned note is counted as skipped.
        if (notes.isEmpty() || notesRepository.saveNotes(notes) is Result.Success) {
            val imported = plans.count { it.successOutcome == ImportOutcome.IMPORTED }
            val overwritten = plans.count { it.successOutcome == ImportOutcome.OVERWRITTEN }
            return ImportSummary(imported = imported, overwritten = overwritten, skipped = initiallySkipped)
        }
        return ImportSummary(imported = 0, overwritten = 0, skipped = initiallySkipped + plans.size)
    }
}
