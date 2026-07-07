package org.skynetsoftware.skeletonnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.util.TagExtractor

/**
 * ViewModel for the note detail screen that handles loading, saving, and deleting notes.
 */
class NoteDetailViewModel(
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val archiveNoteUseCase: ArchiveNoteUseCase,
) : ViewModel() {

    companion object {
        const val NEW_NOTE_ID = -1L

        /**
         * Factory for creating [NoteDetailViewModel] instances with the required dependencies.
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteDetailViewModel(
                    getNoteByIdUseCase = AppDi.getNoteByIdUseCase,
                    saveNoteUseCase = AppDi.saveNoteUseCase,
                    deleteNoteUseCase = AppDi.deleteNoteUseCase,
                    moveToTrashUseCase = AppDi.moveToTrashUseCase,
                    archiveNoteUseCase = AppDi.archiveNoteUseCase,
                )
            }
        }
    }

    /**
     * Represents the possible states of the note detail screen.
     */
    sealed class UiState {
        /** A new note is being created. */
        object NewNote : UiState()

        /** An existing note has been loaded. */
        data class NoteLoaded(val note: Note, val attachments: List<Attachment>) : UiState()

        /** The note is being saved. */
        object Saving : UiState()

        /** The note has been saved successfully. */
        object Saved : UiState()

        /** The note has been deleted. */
        object Deleted : UiState()

        /** The note has been moved to trash. */
        object MovedToTrash : UiState()

        /** The note has been archived. */
        object Archived : UiState()

        /** An error occurred. */
        data class Error(val throwable: Throwable) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.NewNote)
    val uiState: StateFlow<UiState> = _uiState

    private var noteId: Long = NEW_NOTE_ID
    private var currentAttachments: List<Attachment> = emptyList()

    /**
     * Loads the note with the given [id] from the repository and emits [UiState.NoteLoaded]
     * on success or [UiState.Error] on failure.
     */
    fun loadNote(id: Long) {
        noteId = id
        viewModelScope.launch {
            when (val result = getNoteByIdUseCase(id)) {
                is Result.Success -> {
                    currentAttachments = result.data.attachments
                    _uiState.value = UiState.NoteLoaded(result.data.note, result.data.attachments)
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.throwable)
                }
            }
        }
    }

    /**
     * Saves the note with the given [title], [content], and [attachments].
     * Tags are extracted from [content] and stored in the note.
     * On success emits [UiState.Saved], on failure emits [UiState.Error].
     */
    fun saveNote(title: String?, content: String, attachments: List<Attachment>) {
        _uiState.value = UiState.Saving
        currentAttachments = attachments
        viewModelScope.launch {
            val tags = TagExtractor.extractTags(content)
            val now = System.currentTimeMillis()
            val note = Note(
                id = if (noteId == NEW_NOTE_ID) 0L else noteId,
                title = title,
                content = content,
                createdAt = now,
                modifiedAt = now,
                tags = tags,
            )
            val attachmentsWithNoteId = if (noteId != NEW_NOTE_ID) {
                attachments.map { it.copy(noteId = noteId) }
            } else {
                attachments
            }
            when (val result = saveNoteUseCase(NoteWithAttachments(note, attachmentsWithNoteId))) {
                is Result.Success -> {
                    if (noteId == NEW_NOTE_ID) {
                        noteId = result.data
                    }
                    _uiState.value = UiState.Saved
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.throwable)
                }
            }
        }
    }

    /**
     * Deletes the currently loaded note. On success emits [UiState.Deleted],
     * on failure emits [UiState.Error].
     */
    fun deleteNote() {
        _uiState.value = UiState.Saving
        viewModelScope.launch {
            when (val result = deleteNoteUseCase(noteId)) {
                is Result.Success -> {
                    _uiState.value = UiState.Deleted
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.throwable)
                }
            }
        }
    }

    /**
     * Moves the currently loaded note to trash. On success emits [UiState.MovedToTrash],
     * on failure emits [UiState.Error].
     */
    fun moveToTrash() {
        _uiState.value = UiState.Saving
        viewModelScope.launch {
            when (val result = moveToTrashUseCase(noteId)) {
                is Result.Success -> {
                    _uiState.value = UiState.MovedToTrash
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.throwable)
                }
            }
        }
    }

    /**
     * Archives the currently loaded note. On success emits [UiState.Archived],
     * on failure emits [UiState.Error].
     */
    fun archiveNote() {
        _uiState.value = UiState.Saving
        viewModelScope.launch {
            when (val result = archiveNoteUseCase(noteId)) {
                is Result.Success -> {
                    _uiState.value = UiState.Archived
                }
                is Result.Failure -> {
                    _uiState.value = UiState.Error(result.throwable)
                }
            }
        }
    }

    /**
     * Returns the current note ID, or [NEW_NOTE_ID] for new notes.
     */
    fun getNoteId(): Long = noteId

    /**
     * Returns `true` if this is a new note (not yet saved to the repository).
     */
    fun isNewNote(): Boolean = noteId == NEW_NOTE_ID
}
