package org.skynetsoftware.skeletonnotes.domain.di

import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase

/**
 * Dependency injection container for the domain layer.
 * Must be initialized via [init] before accessing any dependencies.
 */
object DomainDi {
    private lateinit var notesRepository: NotesRepository

    /**
     * Initializes the container with the required [notesRepository].
     */
    fun init(notesRepository: NotesRepository) {
        this.notesRepository = notesRepository
    }

    /**
     * Lazily provides the [GetAllNotesUseCase] singleton.
     */
    val getAllNotesUseCase: GetAllNotesUseCase by lazy { GetAllNotesUseCase(notesRepository) }

    /**
     * Lazily provides the [GetNoteByIdUseCase] singleton.
     */
    val getNoteByIdUseCase: GetNoteByIdUseCase by lazy { GetNoteByIdUseCase(notesRepository) }

    /**
     * Lazily provides the [SaveNoteUseCase] singleton.
     */
    val saveNoteUseCase: SaveNoteUseCase by lazy { SaveNoteUseCase(notesRepository) }

    /**
     * Lazily provides the [DeleteNoteUseCase] singleton.
     */
    val deleteNoteUseCase: DeleteNoteUseCase by lazy { DeleteNoteUseCase(notesRepository) }
}
