package org.skynetsoftware.skeletonnotes.data.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.attachment.AttachmentStorageImpl
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStoreImpl
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSourceImpl
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper
import org.skynetsoftware.skeletonnotes.data.di.DataDi.init
import org.skynetsoftware.skeletonnotes.data.network.NextcloudApi
import org.skynetsoftware.skeletonnotes.data.network.NextcloudApiImpl
import org.skynetsoftware.skeletonnotes.data.repository.BackupRepositoryImpl
import org.skynetsoftware.skeletonnotes.data.repository.NextcloudRepositoryImpl
import org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl
import org.skynetsoftware.skeletonnotes.data.repository.SettingsRepositoryImpl
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

/**
 * Dependency injection container for the data layer.
 * Must be initialized via [init] before accessing any dependencies.
 */
object DataDi {

    private lateinit var application: Application
    private var inMemoryDatabase: Boolean = false

    /**
     * Initializes the container with the given [application].
     *
     * @param inMemoryDatabase when `true` the database is created in memory instead of on
     * disk, used by instrumented tests for isolation.
     */
    fun init(application: Application, inMemoryDatabase: Boolean = false) {
        this.application = application
        this.inMemoryDatabase = inMemoryDatabase
    }

    private val skeletonNotesDatabaseHelper by lazy {
        SkeletonNotesDatabaseHelper(
            application,
            databaseName = if (inMemoryDatabase) null else "skeleton-notes",
        )
    }

    private val notesDataSource: NotesDataSource by lazy { NotesDataSourceImpl(skeletonNotesDatabaseHelper) }

    private val nextcloudConfigStore: NextcloudConfigStore by lazy { NextcloudConfigStoreImpl.from(application) }

    private val nextcloudApi: NextcloudApi by lazy { NextcloudApiImpl(nextcloudConfigStore) }

    val attachmentFileStorage: AttachmentFileStorage by lazy { AttachmentStorageImpl(application) }

    val notesRepository: NotesRepository by lazy { NotesRepositoryImpl(notesDataSource) }

    val backupRepository: BackupRepository by lazy {
        BackupRepositoryImpl(notesRepository, attachmentFileStorage)
    }

    val nextcloudRepository: NextcloudRepository by lazy { NextcloudRepositoryImpl(nextcloudApi, nextcloudConfigStore, attachmentFileStorage) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(
            nextcloudConfigStore
        )
    }
}
