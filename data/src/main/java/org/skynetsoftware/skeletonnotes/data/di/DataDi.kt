package org.skynetsoftware.skeletonnotes.data.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

object DataDi {

    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application
    }

    val notesRepository: NotesRepository by lazy { NotesRepositoryImpl(application.filesDir.resolve("notes")) }
}