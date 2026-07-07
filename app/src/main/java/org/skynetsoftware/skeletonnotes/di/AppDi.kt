package org.skynetsoftware.skeletonnotes.di

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.di.DomainDi
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SearchAndFilterNotesUseCase

/**
 * Top-level dependency injection container that initializes all layers.
 * Must be initialized via [init] before accessing any dependencies.
 */
object AppDi {
    private lateinit var application: Application

    /**
     * Initializes the data and domain layers with the given [application].
     */
    fun init(application: Application) {
        this.application = application
        DataDi.init(application)
        DomainDi.init(notesRepository)
    }

    /**
     * Provides the [NotesRepository] singleton.
     */
    val notesRepository: NotesRepository get() = DataDi.notesRepository

    /**
     * Provides the [GetAllNotesUseCase] singleton.
     */
    val getAllNotesUseCase: GetAllNotesUseCase get() = DomainDi.getAllNotesUseCase

    /**
     * Provides the [GetNoteByIdUseCase] singleton.
     */
    val getNoteByIdUseCase: GetNoteByIdUseCase get() = DomainDi.getNoteByIdUseCase

    /**
     * Provides the [SaveNoteUseCase] singleton.
     */
    val saveNoteUseCase: SaveNoteUseCase get() = DomainDi.saveNoteUseCase

    /**
     * Provides the [DeleteNoteUseCase] singleton.
     */
    val deleteNoteUseCase: DeleteNoteUseCase get() = DomainDi.deleteNoteUseCase

    /**
     * Provides the [MoveToTrashUseCase] singleton.
     */
    val moveToTrashUseCase: MoveToTrashUseCase get() = DomainDi.moveToTrashUseCase

    /**
     * Provides the [ArchiveNoteUseCase] singleton.
     */
    val archiveNoteUseCase: ArchiveNoteUseCase get() = DomainDi.archiveNoteUseCase

    /**
     * Provides the [SearchAndFilterNotesUseCase] singleton.
     */
    val searchAndFilterNotesUseCase: SearchAndFilterNotesUseCase get() = DomainDi.searchAndFilterNotesUseCase
}
