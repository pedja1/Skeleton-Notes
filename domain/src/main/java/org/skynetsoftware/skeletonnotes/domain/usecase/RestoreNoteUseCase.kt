package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for restoring a note from trash or archive by ID.
 */
class RestoreNoteUseCase(
    private val notesRepository: NotesRepository,
) {
    /**
     * Restores the note identified by [id], setting its status back to ACTIVE.
     *
     * @param id the ID of the note to restore
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: String): Result<Unit> = notesRepository.restoreNote(id)
}
