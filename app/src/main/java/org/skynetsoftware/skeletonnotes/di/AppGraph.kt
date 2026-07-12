package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.di.DomainDi
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.CreateAttachmentUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteAttachmentLocalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ExportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ImportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.RestoreNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SearchAndFilterNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase

/**
 * The application's object graph: the [Application] plus every use case exposed to the UI
 * layer. Acts as the swappable seam behind [AppDi] so tests can install an alternative
 * implementation (for example one backed by an in-memory database).
 */
interface AppGraph {
    val application: Application
    val getAllNotesUseCase: GetAllNotesUseCase
    val getNoteByIdUseCase: GetNoteByIdUseCase
    val saveNoteUseCase: SaveNoteUseCase
    val exportNotesUseCase: ExportNotesUseCase
    val importNotesUseCase: ImportNotesUseCase
    val deleteNoteUseCase: DeleteNoteUseCase
    val moveToTrashUseCase: MoveToTrashUseCase
    val archiveNoteUseCase: ArchiveNoteUseCase
    val restoreNoteUseCase: RestoreNoteUseCase
    val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase
    val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase
    val getSettingsUseCase: GetSettingsUseCase
    val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase
    val setSyncIntervalUseCase: SetSyncIntervalUseCase
    val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase
    val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase
    val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase
    val nextcloudSyncScheduler: NextcloudSyncScheduler
    val isNextcloudSupported: Boolean
    val createAttachmentUseCase: CreateAttachmentUseCase
    val deleteAttachmentLocalUseCase: DeleteAttachmentLocalUseCase
}

/**
 * Production [AppGraph] that wires the real data and domain layers together.
 *
 * @param application the application context used by the data layer.
 * @param inMemoryDatabase when `true` the data layer is backed by an in-memory database,
 * used by instrumented tests to avoid touching the on-disk database.
 * @param nextcloudRepository the Nextcloud repository implementation.
 * @param settingsRepository the Settings repository implementation.
 */
class ProductionAppGraph(
    override val application: Application,
    inMemoryDatabase: Boolean = false,
    private val nextcloudRepository: NextcloudRepository,
    settingsRepository: SettingsRepository,
    scheduler: NextcloudSyncScheduler,
) : AppGraph {
    init {
        DataDi.init(application, inMemoryDatabase)
        DomainDi.init(
            DataDi.notesRepository,
            nextcloudRepository,
            settingsRepository,
            DataDi.backupRepository,
            DataDi.attachmentFileStorage,
        )
    }

    override val getAllNotesUseCase: GetAllNotesUseCase get() = DomainDi.getAllNotesUseCase

    override val getNoteByIdUseCase: GetNoteByIdUseCase get() = DomainDi.getNoteByIdUseCase

    override val saveNoteUseCase: SaveNoteUseCase get() = DomainDi.saveNoteUseCase

    override val exportNotesUseCase: ExportNotesUseCase get() = DomainDi.exportNotesUseCase

    override val importNotesUseCase: ImportNotesUseCase get() = DomainDi.importNotesUseCase

    override val deleteNoteUseCase: DeleteNoteUseCase get() = DomainDi.deleteNoteUseCase

    override val moveToTrashUseCase: MoveToTrashUseCase get() = DomainDi.moveToTrashUseCase

    override val archiveNoteUseCase: ArchiveNoteUseCase get() = DomainDi.archiveNoteUseCase

    override val restoreNoteUseCase: RestoreNoteUseCase get() = DomainDi.restoreNoteUseCase

    override val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase get() = DomainDi.searchAndFilterNotesUseCase

    override val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase
        get() = DomainDi.syncNotesWithNextcloudUseCase

    override val getSettingsUseCase: GetSettingsUseCase get() = DomainDi.getSettingsUseCase

    override val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase get() = DomainDi.setPeriodicSyncEnabled

    override val setSyncIntervalUseCase: SetSyncIntervalUseCase get() = DomainDi.setSyncIntervalUseCase

    override val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase
        get() = DomainDi.setSyncOnlyOnUnmeteredUseCase

    override val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase
        get() = DomainDi.initiateNextcloudLoginUseCase

    override val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase get() = DomainDi.pollNextcloudLoginUseCase

    override val nextcloudSyncScheduler: NextcloudSyncScheduler = scheduler

    override val isNextcloudSupported: Boolean get() = nextcloudRepository.isSupported()

    override val createAttachmentUseCase: CreateAttachmentUseCase by lazy { DomainDi.createAttachmentUseCase }
    override val deleteAttachmentLocalUseCase: DeleteAttachmentLocalUseCase by lazy {
        DomainDi.deleteAttachmentLocalUseCase
    }
}
