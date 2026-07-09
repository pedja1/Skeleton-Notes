package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.di.DomainDi
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SearchAndFilterNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
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
    val deleteNoteUseCase: DeleteNoteUseCase
    val moveToTrashUseCase: MoveToTrashUseCase
    val archiveNoteUseCase: ArchiveNoteUseCase
    val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase
    val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase
    val getSettingsUseCase: GetSettingsUseCase
    val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase
    val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase
    val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase
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
            DataDi.attachmentFileStorage,
        )
    }

    override val getAllNotesUseCase: GetAllNotesUseCase get() = DomainDi.getAllNotesUseCase

    override val getNoteByIdUseCase: GetNoteByIdUseCase get() = DomainDi.getNoteByIdUseCase

    override val saveNoteUseCase: SaveNoteUseCase get() = DomainDi.saveNoteUseCase

    override val deleteNoteUseCase: DeleteNoteUseCase get() = DomainDi.deleteNoteUseCase

    override val moveToTrashUseCase: MoveToTrashUseCase get() = DomainDi.moveToTrashUseCase

    override val archiveNoteUseCase: ArchiveNoteUseCase get() = DomainDi.archiveNoteUseCase

    override val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase get() = DomainDi.searchAndFilterNotesUseCase

    override val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase get() = DomainDi.syncNotesWithNextcloudUseCase

    override val getSettingsUseCase: GetSettingsUseCase get() = DomainDi.getSettingsUseCase

    override val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase get() = DomainDi.setPeriodicSyncEnabled

    override val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase get() = DomainDi.initiateNextcloudLoginUseCase

    override val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase get() = DomainDi.pollNextcloudLoginUseCase
}
