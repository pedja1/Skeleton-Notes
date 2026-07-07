package org.skynetsoftware.skeletonnotes.data.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSourceImpl
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper
import org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Dependency injection container for the data layer.
 * Must be initialized via [init] before accessing any dependencies.
 */
object DataDi {

    private lateinit var application: Application

    /**
     * Initializes the container with the given [application].
     */
    fun init(application: Application) {
        this.application = application
    }

    /**
     * Lazily provides the [SkeletonNotesDatabaseHelper] singleton.
     */
    internal val skeletonNotesDatabaseHelper by lazy { SkeletonNotesDatabaseHelper(application) }

    /**
     * Lazily provides the [NotesDataSource] singleton.
     */
    internal val notesDataSource: NotesDataSource by lazy { NotesDataSourceImpl(skeletonNotesDatabaseHelper) }

    /**
     * Lazily provides the [NotesRepository] singleton.
     */
    val notesRepository: NotesRepository by lazy { NotesRepositoryImpl(notesDataSource) }
}
