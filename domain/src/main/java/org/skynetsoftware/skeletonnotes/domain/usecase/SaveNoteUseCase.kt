package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for saving (creating or updating) a note with its attachments.
 */
class SaveNoteUseCase(private val notesRepository: NotesRepository) {
    /**
     * Saves the given [noteWithAttachments] and returns the resulting note ID.
     *
     * @param noteWithAttachments the note and its attachments to save
     * @return [Result.Success] with the saved note ID, or [Result.Failure] on error
     */
    suspend operator fun invoke(noteWithAttachments: NoteWithAttachments): Result<Long> =
        notesRepository.saveNote(noteWithAttachments)
}
