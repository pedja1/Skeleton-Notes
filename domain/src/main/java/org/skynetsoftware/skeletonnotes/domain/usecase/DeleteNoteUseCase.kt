package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for deleting a note and its attachments by ID.
 */
class DeleteNoteUseCase(
    private val notesRepository: NotesRepository,
    private val deleteAttachmentLocal: DeleteAttachmentLocalUseCase,
) {
    /**
     * Deletes the note identified by [id] along with all its attachments, including the
     * attachments' local files — the database rows are their only reference, so leaving the
     * files behind would leak storage (and retain user data) forever.
     *
     * @param id the ID of the note to delete
     * @return [Result.Success] on successful deletion, or [Result.Failure] on error
     */
    suspend operator fun invoke(id: String): Result<Unit> {
        val attachmentIds =
            when (val result = notesRepository.getNoteById(id)) {
                is Result.Success -> result.data.attachments.map { it.id }
                is Result.Failure -> emptyList()
            }
        val deleteResult = notesRepository.deleteNote(id)
        if (deleteResult is Result.Success) {
            attachmentIds.forEach { deleteAttachmentLocal(it) }
        }
        return deleteResult
    }
}
