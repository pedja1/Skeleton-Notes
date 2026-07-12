package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.di.AppDi.init
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
import org.skynetsoftware.skeletonnotes.domain.usecase.SetStopRequestingNotificationPermissionUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ShouldStopRequestingNotificationPermissionUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncScheduler

/**
 * Top-level dependency injection container. Delegates to a swappable [AppGraph] so tests can
 * substitute the object graph. Must be initialized via [init] (production) or [install]
 * (tests) before accessing any dependencies.
 */
object AppDi {
    private lateinit var graph: AppGraph

    /**
     * Initializes the container with a [ProductionAppGraph] for the given [application].
     *
     * @param inMemoryDatabase when `true` the data layer uses an in-memory database.
     */
    fun init(
        application: Application,
        inMemoryDatabase: Boolean = false,
    ) {
        install(ProductionAppGraph(application, inMemoryDatabase))
    }

    /**
     * Installs the given [graph], replacing any previously installed one. Intended as the
     * seam for instrumented tests to inject an alternative object graph.
     */
    fun install(graph: AppGraph) {
        this.graph = graph
    }

    val application: Application get() = graph.application

    val getAllNotesUseCase: GetAllNotesUseCase get() = graph.getAllNotesUseCase

    val getNoteByIdUseCase: GetNoteByIdUseCase get() = graph.getNoteByIdUseCase

    val saveNoteUseCase: SaveNoteUseCase get() = graph.saveNoteUseCase

    val exportNotesUseCase: ExportNotesUseCase get() = graph.exportNotesUseCase

    val importNotesUseCase: ImportNotesUseCase get() = graph.importNotesUseCase

    val deleteNoteUseCase: DeleteNoteUseCase get() = graph.deleteNoteUseCase

    val moveToTrashUseCase: MoveToTrashUseCase get() = graph.moveToTrashUseCase

    val archiveNoteUseCase: ArchiveNoteUseCase get() = graph.archiveNoteUseCase

    val restoreNoteUseCase: RestoreNoteUseCase get() = graph.restoreNoteUseCase

    val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase get() = graph.searchAndFilterNotesUseCase

    val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase get() = graph.syncNotesWithNextcloudUseCase

    val getSettingsUseCase: GetSettingsUseCase get() = graph.getSettingsUseCase

    val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase get() = graph.setPeriodicSyncEnabledUseCase

    val setSyncIntervalUseCase: SetSyncIntervalUseCase get() = graph.setSyncIntervalUseCase

    val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase get() = graph.setSyncOnlyOnUnmeteredUseCase

    val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase get() = graph.initiateNextcloudLoginUseCase

    val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase get() = graph.pollNextcloudLoginUseCase

    val nextcloudSyncScheduler: NextcloudSyncScheduler get() = graph.nextcloudSyncScheduler

    val createAttachmentUseCase: CreateAttachmentUseCase get() = graph.createAttachmentUseCase
    val deleteAttachmentLocalUseCase: DeleteAttachmentLocalUseCase get() = graph.deleteAttachmentLocalUseCase

    val shouldStopRequestingNotificationPermissionUseCase: ShouldStopRequestingNotificationPermissionUseCase
        get() = graph.shouldStopRequestingNotificationPermissionUseCase

    val setStopRequestingNotificationPermissionUseCase: SetStopRequestingNotificationPermissionUseCase
        get() = graph.setStopRequestingNotificationPermissionUseCase
}
