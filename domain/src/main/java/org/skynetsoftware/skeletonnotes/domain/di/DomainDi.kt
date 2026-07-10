package org.skynetsoftware.skeletonnotes.domain.di

import org.skynetsoftware.skeletonnotes.domain.di.DomainDi.init
import org.skynetsoftware.skeletonnotes.domain.repository.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
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
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase

/**
 * Dependency injection container for the domain layer.
 * Must be initialized via [init] before accessing any dependencies.
 */
object DomainDi {
    private lateinit var notesRepository: NotesRepository
    private lateinit var nextcloudRepository: NextcloudRepository
    private lateinit var attachmentFileStorage: AttachmentFileStorage

    private lateinit var settingsRepository: SettingsRepository

    /**
     * Initializes the container with the required repositories.
     */
    fun init(
        notesRepository: NotesRepository,
        nextcloudRepository: NextcloudRepository,
        settingsRepository: SettingsRepository,
        attachmentFileStorage: AttachmentFileStorage = NoOpAttachmentFileStorage,
    ) {
        this.notesRepository = notesRepository
        this.nextcloudRepository = nextcloudRepository
        this.settingsRepository = settingsRepository
        this.attachmentFileStorage = attachmentFileStorage
    }

    val getAllNotesUseCase: GetAllNotesUseCase by lazy { GetAllNotesUseCase(notesRepository) }

    val getNoteByIdUseCase: GetNoteByIdUseCase by lazy { GetNoteByIdUseCase(notesRepository) }

    val saveNoteUseCase: SaveNoteUseCase by lazy { SaveNoteUseCase(notesRepository) }

    val deleteNoteUseCase: DeleteNoteUseCase by lazy { DeleteNoteUseCase(notesRepository) }

    val moveToTrashUseCase: MoveToTrashUseCase by lazy { MoveToTrashUseCase(notesRepository) }

    val archiveNoteUseCase: ArchiveNoteUseCase by lazy { ArchiveNoteUseCase(notesRepository) }

    val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase by lazy { SearchAndFilterNotesUseCase() }

    val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase by lazy {
        SyncNotesWithNextcloudUseCase(
            notesRepository,
            nextcloudRepository,
            attachmentFileStorage,
            settingsRepository,
        )
    }

    val getSettingsUseCase: GetSettingsUseCase by lazy {
        GetSettingsUseCase(
            settingsRepository,
            nextcloudRepository,
        )
    }

    val setPeriodicSyncEnabled: SetPeriodicSyncEnabledUseCase by lazy {
        SetPeriodicSyncEnabledUseCase(
            settingsRepository,
        )
    }

    val setSyncIntervalUseCase: SetSyncIntervalUseCase by lazy {
        SetSyncIntervalUseCase(settingsRepository)
    }

    val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase by lazy {
        SetSyncOnlyOnUnmeteredUseCase(settingsRepository)
    }

    val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase by lazy {
        InitiateNextcloudLoginUseCase(
            nextcloudRepository,
        )
    }

    val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase by lazy {
        PollNextcloudLoginUseCase(
            nextcloudRepository,
        )
    }
}
