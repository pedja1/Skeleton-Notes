package org.skynetsoftware.skeletonnotes.data.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSourceImpl
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper
import org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

object DataDi {

    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application
    }

    internal val skeletonNotesDatabaseHelper by lazy { SkeletonNotesDatabaseHelper(application) }
    internal val notesDataSource: NotesDataSource by lazy { NotesDataSourceImpl(skeletonNotesDatabaseHelper) }
    val notesRepository: NotesRepository by lazy { NotesRepositoryImpl(notesDataSource) }
}