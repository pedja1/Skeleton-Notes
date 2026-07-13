package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary
import java.io.InputStream

/**
 * Use case for importing notes and their attachments from a ZIP archive.
 */
class ImportNotesUseCase(
    private val backupRepository: BackupRepository,
) {
    /**
     * Imports notes from [inputStream], delegating id-collision handling to [onConflict] and
     * reporting `current/total` note progress via [onProgress].
     *
     * @return [Result.Success] with an [ImportSummary], or [Result.Failure] on error.
     */
    suspend operator fun invoke(
        inputStream: InputStream,
        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<ImportSummary> = backupRepository.importNotes(inputStream, onConflict, onProgress)
}
