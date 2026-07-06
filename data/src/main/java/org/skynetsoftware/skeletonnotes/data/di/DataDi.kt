package org.skynetsoftware.skeletonnotes.data.di

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.skynetsoftware.skeletonnotes.data.database.NotesDatabase
import org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

object DataDi {

    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application

        val noteDatabase: NotesDatabase = Room.databaseBuilder(application, NotesDatabase::class.java, "skeleton-notes").build()
        runBlocking(Dispatchers.IO) {
            println(noteDatabase.notesDao().getAll())
        }
    }

    val notesRepository: NotesRepository by lazy { NotesRepositoryImpl(application.filesDir.resolve("notes")) }
}