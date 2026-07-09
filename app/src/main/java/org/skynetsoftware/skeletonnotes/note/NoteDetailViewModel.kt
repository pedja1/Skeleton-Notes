package org.skynetsoftware.skeletonnotes.note

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
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.util.TagExtractor
import java.util.UUID

/**
 * ViewModel for the note detail screen that handles loading, saving, and deleting notes.
 */
class NoteDetailViewModel(
    val noteId: String,
    private val isNewNote: Boolean,
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val archiveNoteUseCase: ArchiveNoteUseCase,
) : ViewModel() {

    companion object {

        /**
         * Factory for creating [NoteDetailViewModel] instances with the required dependencies.
         * When [noteId] is null, a new UUID is generated and the ViewModel starts in
         * [UiState.NewNote] without querying the repository.
         */
        fun Factory(noteId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteDetailViewModel(
                    noteId = noteId ?: UUID.randomUUID().toString(),
                    isNewNote = noteId == null,
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

    private var currentAttachments: List<Attachment> = emptyList()
    private var isInitialized = false

    /**
     * The note as loaded from the repository, retained so that a save preserves original metadata
     * (creation timestamp, status, remote sync marker) that the edit screen does not expose.
     */
    private var loadedNote: Note? = null

    init {
        if (isNewNote) {
            isInitialized = true
        } else {
            viewModelScope.launch {
                when (val result = getNoteByIdUseCase(noteId)) {
                    is Result.Success -> {
                        val data = result.data
                        currentAttachments = data.attachments
                        loadedNote = data.note
                        _uiState.value = UiState.NoteLoaded(data.note, data.attachments)
                    }
                    is Result.Failure -> {
                        _uiState.value = UiState.Error(result.throwable)
                    }
                }
                isInitialized = true
            }
        }
    }

    /**
     * Saves the note with the given [title], [content], and [attachments].
     * Tags are extracted from [content] and stored in the note.
     * On success emits [UiState.Saved], on failure emits [UiState.Error].
     */
    fun saveNote(title: String?, content: String, attachments: List<Attachment>) {
        if (uiState.value is UiState.Error) return
        _uiState.value = UiState.Saving
        currentAttachments = attachments
        viewModelScope.launch {
            val tags = TagExtractor.extractTags(content)
            val now = System.currentTimeMillis()
            val existing = loadedNote
            val note = Note(
                id = noteId,
                title = title,
                content = content,
                createdAt = existing?.createdAt ?: now,
                modifiedAt = now,
                tags = tags,
                status = existing?.status ?: NoteStatus.ACTIVE,
                remoteLastModified = existing?.remoteLastModified ?: 0L,
            )
            val attachmentsWithNoteId = attachments.map { it.copy(noteId = noteId) }
            when (val result = saveNoteUseCase(NoteWithAttachments(note, attachmentsWithNoteId))) {
                is Result.Success -> {
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
     * Returns `true` if this is a new note (not yet saved to the repository).
     */
    fun isNewNote(): Boolean = isNewNote
}
