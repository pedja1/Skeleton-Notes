package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for retrieving a single note with its attachments by ID.
 */
class GetNoteByIdUseCase(private val notesRepository: NotesRepository) {
    /**
     * Retrieves the note and its attachments for the given [id].
     *
     * @param id the ID of the note to retrieve
     * @return [Result.Success] with the note and attachments, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: String): Result<NoteWithAttachments> = notesRepository.getNoteById(id)
}
