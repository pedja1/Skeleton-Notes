package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudAttachment
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Orchestrates bidirectional sync between local notes and Nextcloud WebDAV storage.
 * Notes are stored as JSON files in .skeleton_notes/ on the server.
 */
open class SyncNotesWithNextcloudUseCase(
    private val notesRepository: NotesRepository,
    private val nextcloudRepository: NextcloudRepository,
    private val settingsRepository: SettingsRepository,
    private val attachmentFileStorage: AttachmentFileStorage,
) {
    /**
     * Runs a complete sync cycle using internally stored credentials.
     *
     * If the calling coroutine is cancelled mid-sync, the [CancellationException] is rethrown so
     * cancellation stays cooperative (it is not converted into [SyncResult.Error]).
     *
     * @return [SyncResult.Success] if sync completed without conflicts,
     *         [SyncResult.HasConflicts] if any note had both local and remote changes,
     *         [SyncResult.Error] on failure.
     */
    open suspend operator fun invoke(): Result<SyncResult> {
        return try {
            val remoteFileMap =
                when (val result = loadRemoteFileMap()) {
                    is Result.Success -> result.data
                    is Result.Failure -> return Result.Success(SyncResult.Error(result.throwable))
                }

            val localNotes =
                when (val result = notesRepository.getAllNotes()) {
                    is Result.Success -> result.data
                    is Result.Failure -> return Result.Success(SyncResult.Error(result.throwable))
                }

            val localNoteMap = localNotes.associateBy { it.id }
            val conflicts = mutableListOf<NoteConflict>()
            val pushedNotes = mutableListOf<Note>()
            val errors = mutableListOf<Throwable>()

            for (localNote in localNotes) {
                reconcileLocalNote(localNote, remoteFileMap[localNote.id], conflicts, pushedNotes, errors)
            }

            for ((uuid, remoteFile) in remoteFileMap) {
                if (uuid !in localNoteMap) {
                    pullNote(uuid, remoteFile.lastModified, errors)
                }
            }

            trashRemotelyDeletedNotes(localNoteMap, remoteFileMap, errors)
            updatePushedNotesRemoteMtime(pushedNotes, errors)

            if (errors.isNotEmpty()) {
                // At least one note-level operation failed. Do not advance the last-sync timestamp
                // and do not report success, so the failed changes are retried on the next sync.
                return Result.Success(SyncResult.Error(aggregateError(errors)))
            }

            val result =
                if (conflicts.isNotEmpty()) {
                    Result.Success<SyncResult>(SyncResult.HasConflicts(conflicts))
                } else {
                    Result.Success<SyncResult>(SyncResult.Success)
                }
            settingsRepository.setNextcloudLastSyncTimestamp(System.currentTimeMillis())
            result
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Success(SyncResult.Error(t))
        }
    }

    /**
     * Combines multiple per-operation failures into a single throwable, keeping the first as the
     * cause so the original stack trace is preserved.
     */
    private fun aggregateError(errors: List<Throwable>): Throwable {
        if (errors.size == 1) return errors.first()
        return Exception("Sync completed with ${errors.size} failed operation(s)", errors.first())
    }

    /**
     * Lists the remote sync folder and keys the entries by note UUID (filename without .json).
     */
    private suspend fun loadRemoteFileMap(): Result<Map<String, NextcloudFileInfo>> =
        when (val result = nextcloudRepository.listFiles()) {
            is Result.Success -> Result.Success(result.data.associateBy { it.filename.removeSuffix(".json") })
            is Result.Failure -> Result.Failure(result.throwable)
        }

    /**
     * Decides what to do with a single local note: delete remotely when trashed,
     * push when new or only locally changed, pull when only remotely changed, or record a conflict.
     * Notes that are pushed are collected in [pushedNotes] so their remote mtime can be refreshed later.
     */
    private suspend fun reconcileLocalNote(
        localNote: Note,
        remoteFile: NextcloudFileInfo?,
        conflicts: MutableList<NoteConflict>,
        pushedNotes: MutableList<Note>,
        errors: MutableList<Throwable>,
    ) {
        if (localNote.status == NoteStatus.TRASH) {
            recordFailure(nextcloudRepository.deleteNoteDirectory(localNote.id), errors)
            return
        }

        if (remoteFile == null) {
            // Not on the server. Push only if this note was never synced (a new note);
            // a previously-synced note that vanished remotely is a remote deletion and is
            // handled by trashRemotelyDeletedNotes, so it must not be re-uploaded here.
            if (localNote.remoteLastModified == 0L) {
                pushNote(localNote, errors)
                pushedNotes.add(localNote)
            }
            return
        }

        val localChanged = localNote.modifiedAt > localNote.remoteLastModified
        val remoteChanged = remoteFile.lastModified > localNote.remoteLastModified

        when {
            localChanged && remoteChanged ->
                conflicts.add(NoteConflict(localNote = localNote, remoteNoteId = localNote.id))

            remoteChanged -> pullNote(localNote.id, remoteFile.lastModified, errors)
            localChanged -> {
                pushNote(localNote, errors)
                pushedNotes.add(localNote)
            }
        }
    }

    /**
     * Records the throwable of a failed [Result] into [errors] so the overall sync can report
     * failure. Successful results are ignored.
     */
    private fun recordFailure(
        result: Result<Unit>,
        errors: MutableList<Throwable>,
    ) {
        if (result is Result.Failure) errors.add(result.throwable)
    }

    /**
     * Trashes notes that were previously synced but no longer exist on the server.
     * Notes that were never synced ([Note.remoteLastModified] == 0) are local-only and are left untouched,
     * so a freshly created note is not destroyed on the same sync that first pushes it.
     */
    private suspend fun trashRemotelyDeletedNotes(
        localNoteMap: Map<String, Note>,
        remoteFileMap: Map<String, NextcloudFileInfo>,
        errors: MutableList<Throwable>,
    ) {
        val remoteDeletedIds =
            localNoteMap
                .filterValues { it.remoteLastModified > 0L }
                .keys - remoteFileMap.keys
        for (deletedId in remoteDeletedIds) {
            recordFailure(notesRepository.moveToTrash(deletedId), errors)
        }
    }

    /**
     * Re-lists the remote folder once and records the server's post-upload mtime for each pushed note,
     * so the next sync does not see the just-pushed note as remotely changed (which would cause a spurious conflict).
     */
    private suspend fun updatePushedNotesRemoteMtime(
        pushedNotes: List<Note>,
        errors: MutableList<Throwable>,
    ) {
        if (pushedNotes.isEmpty()) return

        val refreshed =
            when (val result = loadRemoteFileMap()) {
                is Result.Success -> result.data
                is Result.Failure -> {
                    errors.add(result.throwable)
                    return
                }
            }

        for (note in pushedNotes) {
            val mtime = refreshed[note.id]?.lastModified ?: note.modifiedAt
            val current =
                when (val result = notesRepository.getNoteById(note.id)) {
                    is Result.Success -> result.data
                    else -> NoteWithAttachments(note, emptyList())
                }
            recordFailure(
                notesRepository.saveNote(
                    NoteWithAttachments(
                        current.note.copy(remoteLastModified = mtime),
                        current.attachments,
                    ),
                ),
                errors,
            )
        }
    }

    /**
     * Downloads a remote note and its attachments, then saves them locally.
     *
     * @param uuid the remote note UUID (filename without .json)
     * @param remoteLastModified the WebDAV file mtime for change detection
     */
    private suspend fun pullNote(
        uuid: String,
        remoteLastModified: Long,
        errors: MutableList<Throwable>,
    ) {
        val remoteNote =
            when (val result = nextcloudRepository.downloadNote(uuid)) {
                is Result.Success -> result.data
                is Result.Failure -> {
                    errors.add(result.throwable)
                    return
                }
            }

        val existing =
            when (val result = notesRepository.getNoteById(uuid)) {
                is Result.Success -> result.data
                else -> null
            }

        val note = remoteNote.toNote(uuid, remoteLastModified)

        val existingAttachments = existing?.attachments ?: emptyList()
        val existingAttachmentMap = existingAttachments.associateBy { it.id }

        val mergedAttachments = mutableListOf<Attachment>()

        for (remoteAttachment in remoteNote.attachments) {
            val localAttachment = existingAttachmentMap[remoteAttachment.id]
            if (localAttachment != null && localAttachment.uri.isNotBlank()) {
                mergedAttachments.add(localAttachment)
            } else {
                val result =
                    nextcloudRepository.downloadAttachment(
                        noteId = uuid,
                        attachmentId = remoteAttachment.id,
                        filename = remoteAttachment.filename,
                    )

                val filePath =
                    when (result) {
                        is Result.Success -> result.data
                        is Result.Failure -> null
                    }

                if (filePath == null) continue
                mergedAttachments.add(
                    Attachment(
                        id = remoteAttachment.id,
                        noteId = uuid,
                        uri = filePath,
                        mimeType = remoteAttachment.mimeType,
                    ),
                )
            }
        }

        recordFailure(notesRepository.saveNote(NoteWithAttachments(note, mergedAttachments)), errors)
    }

    /**
     * Uploads a local note and its attachment files to the remote server.
     * The note's [Note.remoteLastModified] is refreshed later by [updatePushedNotesRemoteMtime]
     * once the server's post-upload mtime is known.
     *
     * @param note the local note to push
     * @param errors accumulator for failed upload operations
     */
    private suspend fun pushNote(
        note: Note,
        errors: MutableList<Throwable>,
    ) {
        val localNoteWithAttachments =
            when (val result = notesRepository.getNoteById(note.id)) {
                is Result.Success -> result.data
                else -> NoteWithAttachments(note, emptyList())
            }

        val nextcloudAttachments = mutableListOf<NextcloudAttachment>()
        for (attachment in localNoteWithAttachments.attachments) {
            val filename = attachment.uri.substringAfterLast("/")
            nextcloudAttachments.add(
                NextcloudAttachment(
                    id = attachment.id,
                    filename = filename,
                    mimeType = attachment.mimeType,
                ),
            )
            val file = attachmentFileStorage.getFile(attachment.id)
            if (file.exists()) {
                file.inputStream().use { inputStream ->
                    recordFailure(
                        nextcloudRepository.uploadAttachment(
                            noteId = note.id,
                            attachmentId = attachment.id,
                            filename = filename,
                            inputStream = inputStream,
                            contentLength = file.length(),
                        ),
                        errors,
                    )
                }
            }
        }

        val remoteNote = note.toNextcloudNote(nextcloudAttachments)
        recordFailure(nextcloudRepository.uploadNote(remoteNote), errors)
    }

    companion object {
        /**
         * Converts a [NextcloudNote] to a local [Note] with the given [uuid].
         *
         * @param remoteLastModified the WebDAV file mtime for accurate change detection
         */
        internal fun NextcloudNote.toNote(
            uuid: String,
            remoteLastModified: Long,
        ): Note =
            Note(
                id = uuid,
                title = title,
                content = content,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                tags = tags,
                status =
                    try {
                        NoteStatus.valueOf(status)
                    } catch (_: IllegalArgumentException) {
                        NoteStatus.ACTIVE
                    },
                remoteLastModified = remoteLastModified,
            )

        /**
         * Converts a local [Note] to a [NextcloudNote] for remote storage.
         *
         * @param attachments list of attachment references to include
         */
        internal fun Note.toNextcloudNote(attachments: List<NextcloudAttachment>): NextcloudNote =
            NextcloudNote(
                id = id,
                title = title,
                content = content,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                tags = tags,
                status = status.name,
                attachments = attachments,
            )
    }
}

/**
 * Result of a sync operation.
 */
sealed class SyncResult {
    /** Sync completed without conflicts. */
    data object Success : SyncResult()

    /** Sync completed but some notes had conflicts that were skipped. */
    data class HasConflicts(
        val conflicts: List<NoteConflict>,
    ) : SyncResult()

    /** Sync failed due to an error. */
    data class Error(
        val throwable: Throwable,
    ) : SyncResult()
}

/**
 * Represents a sync conflict where both local and remote versions have changed.
 */
data class NoteConflict(
    val localNote: Note,
    val remoteNoteId: String,
)
