package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import java.io.OutputStream

/**
 * Use case for exporting all notes and their attachments to a ZIP archive.
 */
class ExportNotesUseCase(
    private val backupRepository: BackupRepository,
) {
    /**
     * Exports all notes to [outputStream].
     *
     * @return [Result.Success] with the number of notes exported, or [Result.Failure] on error.
     */
    suspend operator fun invoke(outputStream: OutputStream): Result<Int> = backupRepository.exportNotes(outputStream)
}
