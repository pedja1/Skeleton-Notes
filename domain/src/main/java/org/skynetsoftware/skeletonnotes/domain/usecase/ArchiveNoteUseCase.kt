package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for archiving a note by ID.
 */
class ArchiveNoteUseCase(private val notesRepository: NotesRepository) {
    /**
     * Archives the note identified by [id].
     *
     * @param id the ID of the note to archive
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: Long): Result<Unit> = notesRepository.archiveNote(id)
}
