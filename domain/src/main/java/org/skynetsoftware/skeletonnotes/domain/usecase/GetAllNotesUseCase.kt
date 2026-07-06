package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class GetAllNotesUseCase(private val notesRepository: NotesRepository) {
    operator fun invoke(): Flow<Result<List<Note>>> = flow {
        emit(notesRepository.getAllNotes())
    }
}
