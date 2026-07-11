package org.skynetsoftware.skeletonnotes.note

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.CreateAttachmentUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteAttachmentLocalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.RestoreNoteUseCase
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
    private val restoreNoteUseCase: RestoreNoteUseCase,
    private val createAttachment: CreateAttachmentUseCase,
    private val deleteAttachmentLocal: DeleteAttachmentLocalUseCase,
) : ViewModel() {
    companion object {
        private const val TAG = "NoteDetailsViewModel"

        /**
         * Factory for creating [NoteDetailViewModel] instances with the required dependencies.
         * When [noteId] is null, a new UUID is generated and the ViewModel starts in
         * [UiState.NewNote] without querying the repository.
         */
        fun Factory(noteId: String?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NoteDetailViewModel(
                        noteId = noteId ?: UUID.randomUUID().toString(),
                        isNewNote = noteId == null,
                        getNoteByIdUseCase = AppDi.getNoteByIdUseCase,
                        saveNoteUseCase = AppDi.saveNoteUseCase,
                        deleteNoteUseCase = AppDi.deleteNoteUseCase,
                        moveToTrashUseCase = AppDi.moveToTrashUseCase,
                        archiveNoteUseCase = AppDi.archiveNoteUseCase,
                        restoreNoteUseCase = AppDi.restoreNoteUseCase,
                        createAttachment = AppDi.createAttachmentUseCase,
                        deleteAttachmentLocal = AppDi.deleteAttachmentLocalUseCase,
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
        data class NoteLoaded(
            val note: Note,
            val attachments: List<Attachment>,
        ) : UiState()

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

        /** The note has been restored from trash or archive. */
        object Restored : UiState()

        /** An error occurred. */
        data class Error(
            val throwable: Throwable,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.NewNote)
    val uiState: StateFlow<UiState> = _uiState

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> get() = _attachments.asStateFlow()

    private val _showToast = MutableSharedFlow<String>(replay = 0)
    val showToast: Flow<String> get() = _showToast

    private var isInitialized = false

    /**
     * The note as loaded from the repository, retained so that a save preserves original metadata
     * (creation timestamp, status, remote sync marker) that the edit screen does not expose.
     */
    private var loadedNote: NoteWithAttachments? = null

    init {
        if (isNewNote) {
            isInitialized = true
        } else {
            viewModelScope.launch {
                when (val result = getNoteByIdUseCase(noteId)) {
                    is Result.Success -> {
                        val data = result.data
                        _attachments.value = data.attachments
                        loadedNote = data
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
     * [content] is the HTML representation stored on the note, while [plainTextContent]
     * is the markup-free text used for tag extraction (HTML markup can split a `#tag`
     * across inline elements and hide it from the extractor).
     * On success emits [UiState.Saved], on failure emits [UiState.Error].
     */
    fun saveNote(
        title: String?,
        content: String,
        plainTextContent: String,
    ) {
        if (uiState.value is UiState.Error) return
        val attachmentsUnchanged =
            attachments.value.map { it.id }.toSet() ==
                loadedNote
                    ?.attachments
                    ?.map { it.id }
                    .orEmpty()
                    .toSet()
        if (!isNewNote &&
            title == loadedNote?.note?.title &&
            content == loadedNote?.note?.content &&
            attachmentsUnchanged
        ) {
            _uiState.value = UiState.Saved
            return
        }
        _uiState.value = UiState.Saving
        viewModelScope.launch {
            val tags = TagExtractor.extractTags(plainTextContent)
            val now = System.currentTimeMillis()
            val existing = loadedNote
            val note =
                Note(
                    id = noteId,
                    title = title,
                    content = content,
                    createdAt = existing?.note?.createdAt ?: now,
                    modifiedAt = now,
                    tags = tags,
                    status = existing?.note?.status ?: NoteStatus.ACTIVE,
                    remoteLastModified = existing?.note?.remoteLastModified ?: 0L,
                )
            val attachmentsWithNoteId = attachments.value.map { it.copy(noteId = noteId) }
            when (val result = saveNoteUseCase(NoteWithAttachments(note, attachmentsWithNoteId))) {
                is Result.Success -> {
                    val removedIds =
                        loadedNote
                            ?.attachments
                            ?.map { it.id }
                            .orEmpty()
                            .toSet() -
                            attachments.value.map { it.id }.toSet()
                    removedIds.forEach { deleteAttachmentLocal(it) }
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
     * Restores the currently loaded note from trash or archive. On success emits
     * [UiState.Restored], on failure emits [UiState.Error].
     */
    fun restoreNote() {
        _uiState.value = UiState.Saving
        viewModelScope.launch {
            when (val result = restoreNoteUseCase(noteId)) {
                is Result.Success -> {
                    _uiState.value = UiState.Restored
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

    /**
     * Returns the [NoteStatus] of the currently loaded note, or `null` if no note
     * has been loaded yet (e.g. a new note).
     */
    fun noteStatus(): NoteStatus? = loadedNote?.note?.status

    fun onRemoveAttachment(attachment: Attachment) {
        _attachments.value = _attachments.value.filter { it.id != attachment.id }
    }

    fun onAttachmentPicked(uri: Uri?) =
        viewModelScope.launch {
            if (uri == null) return@launch

            val createAttachmentResult =
                createAttachment(
                    noteId = noteId,
                    sourceUri = uri.toString(),
                    mimeType = AppDi.application.contentResolver.getType(uri),
                    filename = resolveDisplayName(uri),
                )
            when (createAttachmentResult) {
                is Result.Failure<Attachment> -> {
                    Log.w(TAG, null, createAttachmentResult.throwable)
                    _showToast.emit(AppDi.application.getString(R.string.note_details_error_adding_attachment))
                }
                is Result.Success<Attachment> -> {
                    val mutableAttachments = attachments.value.toMutableList()
                    mutableAttachments.add(createAttachmentResult.data)
                    _attachments.value = mutableAttachments
                }
            }
        }

    /**
     * Resolves the human-readable display name for a picked [uri] via [OpenableColumns.DISPLAY_NAME],
     * or `null` if it cannot be determined. Used to preserve the original filename for display.
     */
    private fun resolveDisplayName(uri: Uri): String? =
        runCatching {
            AppDi.application.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
}
