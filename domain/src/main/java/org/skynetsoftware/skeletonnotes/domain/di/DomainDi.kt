package org.skynetsoftware.skeletonnotes.domain.di

import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

object DomainDi {
    private lateinit var notesRepository: NotesRepository

    fun init(notesRepository: NotesRepository) {
        this.notesRepository = notesRepository
    }

    val getAllNotesUseCase: GetAllNotesUseCase by lazy { GetAllNotesUseCase(notesRepository) }
}
