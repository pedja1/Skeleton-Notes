package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.ArchiveNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.DeleteNoteUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetNoteByIdUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.MoveToTrashUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SaveNoteUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {

    @Test
    fun initialStateIsNewNote() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val viewModel = createViewModel()
            assertEquals(NoteDetailViewModel.UiState.NewNote, viewModel.uiState.value)
            assertTrue(viewModel.isNewNote())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadNoteEmitsNoteLoaded() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 1, title = "Test", content = "<b>Content</b>",
                createdAt = 1000L, modifiedAt = 1000L, tags = setOf("tag")
            )
            val attachments = listOf(Attachment(1, 1, "file://img.png"))
            val repository = FakeNoteDetailRepository(
                note = note,
                attachments = attachments
            )
            val viewModel = createViewModel(repository)

            viewModel.loadNote(1)

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.NoteLoaded)
            val loaded = state as NoteDetailViewModel.UiState.NoteLoaded
            assertEquals("Test", loaded.note.title)
            assertEquals(setOf("tag"), loaded.note.tags)
            assertEquals(1, loaded.attachments.size)
            assertFalse(viewModel.isNewNote())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadNoteEmitsErrorOnFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = FakeNoteDetailRepository(shouldFailLoad = true)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(1)

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun saveNoteExtractsTags() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = FakeNoteDetailRepository()
            val viewModel = createViewModel(repository)

            viewModel.saveNote("Title", "<p>Hello #world and #foo</p>", emptyList())

            val state = viewModel.uiState.value
            assertEquals(NoteDetailViewModel.UiState.Saved, state)
            val savedNote = repository.savedNoteWithAttachments
            assertEquals(setOf("world", "foo"), savedNote?.note?.tags)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun saveNoteEmitsErrorOnRepositoryFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = FakeNoteDetailRepository(shouldFailSave = true)
            val viewModel = createViewModel(repository)

            viewModel.saveNote("Title", "Content", emptyList())

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun saveNewNoteAssignsId() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = FakeNoteDetailRepository(savedId = 42L)
            val viewModel = createViewModel(repository)

            assertTrue(viewModel.isNewNote())
            viewModel.saveNote("New", "Content", emptyList())
            assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
            assertEquals(42L, viewModel.getNoteId())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deleteNoteEmitsDeleted() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Delete", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

            viewModel.deleteNote()
            assertEquals(NoteDetailViewModel.UiState.Deleted, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deleteNoteEmitsErrorOnFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Delete", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note, shouldFailDelete = true)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            viewModel.deleteNote()

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savePreservesExistingNoteId() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 10, title = "Existing", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(10)

            viewModel.saveNote("Updated", "Updated content", emptyList())
            assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
            assertEquals(10L, viewModel.getNoteId())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun moveToTrashEmitsMovedToTrash() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Trash", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

            viewModel.moveToTrash()
            assertEquals(NoteDetailViewModel.UiState.MovedToTrash, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun moveToTrashEmitsErrorOnFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Trash", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note, shouldFailMoveToTrash = true)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            viewModel.moveToTrash()

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun archiveNoteEmitsArchived() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Archive", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

            viewModel.archiveNote()
            assertEquals(NoteDetailViewModel.UiState.Archived, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun archiveNoteEmitsErrorOnFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val note = Note(
                id = 5, title = "To Archive", content = "Content",
                createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()
            )
            val repository = FakeNoteDetailRepository(note = note, shouldFailArchive = true)
            val viewModel = createViewModel(repository)

            viewModel.loadNote(5)
            viewModel.archiveNote()

            val state = viewModel.uiState.value
            assertTrue(state is NoteDetailViewModel.UiState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        repository: FakeNoteDetailRepository = FakeNoteDetailRepository()
    ): NoteDetailViewModel {
        return NoteDetailViewModel(
            getNoteByIdUseCase = GetNoteByIdUseCase(repository),
            saveNoteUseCase = SaveNoteUseCase(repository),
            deleteNoteUseCase = DeleteNoteUseCase(repository),
            moveToTrashUseCase = MoveToTrashUseCase(repository),
            archiveNoteUseCase = ArchiveNoteUseCase(repository),
        )
    }

    private class FakeNoteDetailRepository(
        private val note: Note? = null,
        private val attachments: List<Attachment> = emptyList(),
        private val savedId: Long = 1L,
        private val shouldFailLoad: Boolean = false,
        private val shouldFailSave: Boolean = false,
        private val shouldFailDelete: Boolean = false,
        private val shouldFailMoveToTrash: Boolean = false,
        private val shouldFailArchive: Boolean = false,
    ) : NotesRepository {
        var savedNoteWithAttachments: NoteWithAttachments? = null

        override suspend fun getAllNotes(): Result<List<Note>> =
            Result.Success(emptyList())

        override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> {
            if (shouldFailLoad) return Result.Failure(RuntimeException("Load error"))
            return Result.Success(NoteWithAttachments(note!!, attachments))
        }

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> {
            if (shouldFailSave) return Result.Failure(RuntimeException("Save error"))
            savedNoteWithAttachments = noteWithAttachments
            return Result.Success(savedId)
        }

        override suspend fun deleteNote(id: Long): Result<Unit> {
            if (shouldFailDelete) return Result.Failure(RuntimeException("Delete error"))
            return Result.Success(Unit)
        }

        override suspend fun moveToTrash(id: Long): Result<Unit> {
            if (shouldFailMoveToTrash) return Result.Failure(RuntimeException("Trash error"))
            return Result.Success(Unit)
        }

        override suspend fun archiveNote(id: Long): Result<Unit> {
            if (shouldFailArchive) return Result.Failure(RuntimeException("Archive error"))
            return Result.Success(Unit)
        }

    }
}
