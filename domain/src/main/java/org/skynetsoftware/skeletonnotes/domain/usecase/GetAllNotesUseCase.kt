package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for retrieving all notes together with their attachments from the repository as a
 * [Flow]. The attachments let the notes list render image previews.
 */
class GetAllNotesUseCase(private val notesRepository: NotesRepository) {
    /**
     * Invokes the use case and returns a [Flow] emitting the result of fetching all notes with
     * their attachments.
     */
    operator fun invoke(): Flow<Result<List<NoteWithAttachments>>> = notesRepository.getAllNotesWithAttachmentsFlow()
}
