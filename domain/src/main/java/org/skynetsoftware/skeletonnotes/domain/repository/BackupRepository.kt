package org.skynetsoftware.skeletonnotes.domain.repository

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.io.InputStream
import java.io.OutputStream

/**
 * How to resolve an import conflict where an incoming note shares its id with an
 * existing local note.
 */
enum class ConflictResolution {
    /** Keep the local note unchanged and discard the incoming one. */
    KEEP_EXISTING,

    /** Replace the local note (and its attachments) with the incoming one. */
    OVERWRITE,

    /** Import the incoming note as a brand-new note with fresh ids. */
    KEEP_BOTH,
}

/**
 * Outcome of an import operation.
 *
 * @param imported number of notes added (no conflict, or resolved as [ConflictResolution.KEEP_BOTH])
 * @param overwritten number of existing notes replaced ([ConflictResolution.OVERWRITE])
 * @param skipped number of incoming notes discarded ([ConflictResolution.KEEP_EXISTING] or unreadable)
 */
data class ImportSummary(
    val imported: Int,
    val overwritten: Int,
    val skipped: Int,
)

/**
 * Repository for exporting all notes (with attachments) to, and importing them from,
 * a single ZIP archive. Implemented in the data layer using only [java.io]/[java.util.zip]
 * so the domain stays free of Android specifics.
 */
interface BackupRepository {
    /**
     * Writes every note, together with its attachment files, as a ZIP archive to
     * [outputStream]. The stream is fully consumed but not closed by this method.
     *
     * [onProgress] is invoked as notes are written with `current` (notes done so far) and
     * `total` (notes to export), so callers can surface `current/total` progress.
     *
     * @return [Result.Success] with the number of notes exported, or [Result.Failure] on error.
     */
    suspend fun exportNotes(
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int>

    /**
     * Reads a ZIP archive produced by [exportNotes] from [inputStream] and imports its notes.
     * For every incoming note whose id already exists locally, [onConflict] is invoked with the
     * existing and incoming note and must return the [ConflictResolution] to apply.
     *
     * [onProgress] is invoked as incoming notes are processed with `current` (notes handled so
     * far) and `total` (notes in the archive), so callers can surface `current/total` progress.
     *
     * @return [Result.Success] with an [ImportSummary], or [Result.Failure] on error.
     */
    suspend fun importNotes(
        inputStream: InputStream,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<ImportSummary>
}
