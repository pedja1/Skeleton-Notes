package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.di.DomainDi
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.CreateAttachmentUseCase
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
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncScheduler
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncSchedulerImpl

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
    val createAttachmentUseCase: CreateAttachmentUseCase
}

/**
 * Production [AppGraph] that wires the real data and domain layers together.
 *
 * @param application the application context used by the data layer.
 * @param inMemoryDatabase when `true` the data layer is backed by an in-memory database,
 * used by instrumented tests to avoid touching the on-disk database.
 */
class ProductionAppGraph(
    override val application: Application,
    inMemoryDatabase: Boolean = false,
) : AppGraph {

    init {
        DataDi.init(application, inMemoryDatabase)
        DomainDi.init(
            DataDi.notesRepository,
            DataDi.nextcloudRepository,
            DataDi.settingsRepository,
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

    override val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase get() = DomainDi.syncNotesWithNextcloudUseCase

    override val getSettingsUseCase: GetSettingsUseCase get() = DomainDi.getSettingsUseCase

    override val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase get() = DomainDi.setPeriodicSyncEnabled

    override val setSyncIntervalUseCase: SetSyncIntervalUseCase get() = DomainDi.setSyncIntervalUseCase

    override val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase get() = DomainDi.setSyncOnlyOnUnmeteredUseCase

    override val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase get() = DomainDi.initiateNextcloudLoginUseCase

    override val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase get() = DomainDi.pollNextcloudLoginUseCase

    override val nextcloudSyncScheduler: NextcloudSyncScheduler by lazy {
        NextcloudSyncSchedulerImpl(application, DomainDi.getSettingsUseCase)
    }
    override val createAttachmentUseCase: CreateAttachmentUseCase by lazy { DomainDi.createAttachmentUseCase }
}
