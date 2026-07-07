package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Use case for retrieving all notes from the repository as a [Flow].
 */
class GetAllNotesUseCase(private val notesRepository: NotesRepository) {
    /**
     * Invokes the use case and returns a [Flow] emitting the result of fetching all notes.
     */
    operator fun invoke(): Flow<Result<List<Note>>> =
        flow {
            emit(notesRepository.getAllNotes())
        }
}
