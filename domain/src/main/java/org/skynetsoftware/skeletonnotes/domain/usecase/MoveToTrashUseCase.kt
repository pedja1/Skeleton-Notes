package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for moving a note to trash (soft-delete) by ID.
 */
class MoveToTrashUseCase(
    private val notesRepository: NotesRepository,
) {
    /**
     * Moves the note identified by [id] to trash.
     *
     * @param id the ID of the note to move to trash
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: String): Result<Unit> = notesRepository.moveToTrash(id)
}
