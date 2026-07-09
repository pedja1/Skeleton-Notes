package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for deleting a note and its attachments by ID.
 */
class DeleteNoteUseCase(private val notesRepository: NotesRepository) {
    /**
     * Deletes the note identified by [id] along with all its attachments.
     *
     * @param id the ID of the note to delete
     * @return [Result.Success] on successful deletion, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: String): Result<Unit> = notesRepository.deleteNote(id)
}
