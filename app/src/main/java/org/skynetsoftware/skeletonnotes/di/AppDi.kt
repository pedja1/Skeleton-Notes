package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.di.DomainDi
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

object AppDi {
    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application
        DataDi.init(application)
        DomainDi.init(notesRepository)
    }

    val notesRepository: NotesRepository get() = DataDi.notesRepository
    val getAllNotesUseCase: GetAllNotesUseCase get() = DomainDi.getAllNotesUseCase
}
