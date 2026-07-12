package org.skynetsoftware.skeletonnotes.nextcloud.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase
import org.skynetsoftware.skeletonnotes.nextcloud.config.NextcloudConfigStoreImpl
import org.skynetsoftware.skeletonnotes.nextcloud.network.NextcloudApiImpl
import org.skynetsoftware.skeletonnotes.nextcloud.repository.NextcloudRepositoryImpl
import org.skynetsoftware.skeletonnotes.nextcloud.repository.SettingsRepositoryImpl
import org.skynetsoftware.skeletonnotes.nextcloud.sync.NextcloudSyncSchedulerImpl

/**
 * Dependency injection container for the Nextcloud module.
 * Must be initialized via [init] before accessing any dependencies.
 */
object NextcloudDi {
    private lateinit var application: Application

    private val nextcloudConfigStore by lazy { NextcloudConfigStoreImpl.from(application) }

    private val nextcloudApi by lazy {
        val versionName =
            try {
                application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: "dev"
            } catch (_: Exception) {
                "dev"
            }
        NextcloudApiImpl(nextcloudConfigStore, versionName)
    }

    /**
     * Initializes the container with the required context.
     */
    fun init(application: Application) {
        this.application = application
    }

    /**
     * Creates a production [NextcloudRepository] backed by OkHttp WebDAV.
     */
    fun createNextcloudRepository(attachmentFileStorage: AttachmentFileStorage): NextcloudRepository =
        NextcloudRepositoryImpl(nextcloudApi, nextcloudConfigStore, attachmentFileStorage)

    /**
     * Creates a production [SettingsRepository] backed by SharedPreferences.
     */
    fun createSettingsRepository(): SettingsRepository = SettingsRepositoryImpl(nextcloudConfigStore)

    /**
     * Creates the [SyncNotesWithNextcloudUseCase] wired with production dependencies.
     */
    fun createSyncNotesWithNextcloudUseCase(
        notesRepository: NotesRepository,
        nextcloudRepository: NextcloudRepository,
        settingsRepository: SettingsRepository,
        attachmentFileStorage: AttachmentFileStorage,
    ): SyncNotesWithNextcloudUseCase =
        SyncNotesWithNextcloudUseCase(notesRepository, nextcloudRepository, settingsRepository, attachmentFileStorage)

    /**
     * Creates the [NextcloudSyncScheduler] for production.
     */
    fun createSyncScheduler(getSettingsUseCase: GetSettingsUseCase): NextcloudSyncScheduler =
        NextcloudSyncSchedulerImpl(application, getSettingsUseCase)
}
