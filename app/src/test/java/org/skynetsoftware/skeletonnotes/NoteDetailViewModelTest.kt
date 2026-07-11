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
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
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
import org.skynetsoftware.skeletonnotes.note.NoteDetailViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {
    @Test
    fun initialStateIsNewNoteForNewNoteId() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository()
                val viewModel = createViewModel("new-id", repository, isNewNote = true)
                assertTrue(viewModel.isNewNote())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun loadsExistingNoteOnInit() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "1",
                        title = "Test",
                        content = "<b>Content</b>",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = setOf("tag"),
                    )
                val attachments = listOf(Attachment("1", "1", "file://img.png"))
                val repository =
                    FakeNoteDetailRepository(
                        note = note,
                        attachments = attachments,
                    )
                val viewModel = createViewModel("1", repository)

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
    fun loadErrorsEmitErrorState() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository(shouldFailLoad = true)
                val viewModel = createViewModel("missing", repository)
                testScheduler.advanceUntilIdle()

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveBlockedWhenLoadFailed() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository(shouldFailLoad = true)
                val viewModel = createViewModel("existing", repository)

                viewModel.saveNote("Title", "Content", "Content")

                assertTrue(repository.savedNoteWithAttachments == null)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveNoteExtractsTags() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository()
                val viewModel = createViewModel("", repository, isNewNote = true)

                viewModel.saveNote("Title", "<p>Hello #world and #foo</p>", "Hello #world and #foo")

                val state = viewModel.uiState.value
                assertEquals(NoteDetailViewModel.UiState.Saved, state)
                val savedNote = repository.savedNoteWithAttachments
                assertEquals(setOf("world", "foo"), savedNote?.note?.tags)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveNoteExtractsTagsFromPlainTextEvenWhenHtmlWrapsThem() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository()
                val viewModel = createViewModel("", repository, isNewNote = true)

                // HTML content wraps the tag word in inline markup (as IME spell-check spans do),
                // which would hide it from a regex over the HTML. Plain text keeps it intact.
                viewModel.saveNote("Title", "<p>#<u>LinuxRules</u></p>", "#LinuxRules")

                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                assertEquals(setOf("LinuxRules"), repository.savedNoteWithAttachments?.note?.tags)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveNoteEmitsErrorOnRepositoryFailure() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository(shouldFailSave = true)
                val viewModel = createViewModel("", repository, isNewNote = true)

                viewModel.saveNote("Title", "Content", "Content")

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savesNoteWithExistingId() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val repository = FakeNoteDetailRepository()
                val viewModel = createViewModel("existing-id", repository, isNewNote = true)

                viewModel.saveNote("New", "Content", "Content")
                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                assertEquals("existing-id", viewModel.noteId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun deleteNoteEmitsDeleted() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Delete",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("5", repository)

                assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

                viewModel.deleteNote()
                assertEquals(NoteDetailViewModel.UiState.Deleted, viewModel.uiState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun deleteNoteEmitsErrorOnFailure() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Delete",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note, shouldFailDelete = true)
                val viewModel = createViewModel("5", repository)

                viewModel.deleteNote()

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savePreservesExistingNoteId() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "10",
                        title = "Existing",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("10", repository)

                viewModel.saveNote("Updated", "Updated content", "Updated content")
                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                assertEquals("10", viewModel.noteId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savePreservesOriginalMetadataWhenEditingExistingNote() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "10",
                        title = "Existing",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 2000L,
                        tags = emptySet(),
                        status = NoteStatus.ARCHIVE,
                        remoteLastModified = 1500L,
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("10", repository)

                viewModel.saveNote("Updated", "Updated content", "Updated content")

                val saved = repository.savedNoteWithAttachments?.note
                assertEquals(1000L, saved?.createdAt)
                assertEquals(NoteStatus.ARCHIVE, saved?.status)
                assertEquals(1500L, saved?.remoteLastModified)
                assertTrue((saved?.modifiedAt ?: 0L) > 2000L)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun moveToTrashEmitsMovedToTrash() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Trash",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("5", repository)

                assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

                viewModel.moveToTrash()
                assertEquals(NoteDetailViewModel.UiState.MovedToTrash, viewModel.uiState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun moveToTrashEmitsErrorOnFailure() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Trash",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note, shouldFailMoveToTrash = true)
                val viewModel = createViewModel("5", repository)

                viewModel.moveToTrash()

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun archiveNoteEmitsArchived() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Archive",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("5", repository)

                assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

                viewModel.archiveNote()
                assertEquals(NoteDetailViewModel.UiState.Archived, viewModel.uiState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun archiveNoteEmitsErrorOnFailure() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "To Archive",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note, shouldFailArchive = true)
                val viewModel = createViewModel("5", repository)

                viewModel.archiveNote()

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savingUnchangedNoteSkipsRepositoryCall() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "10",
                        title = "Existing",
                        content = "<b>Content</b>",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("10", repository)

                viewModel.saveNote("Existing", "<b>Content</b>", "Content")
                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                assertTrue(repository.savedNoteWithAttachments == null)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savingChangedContentStillCallsRepository() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "10",
                        title = "Existing",
                        content = "<b>Content</b>",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("10", repository)

                viewModel.saveNote("Existing", "<b>Changed</b>", "Changed")
                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                assertTrue(repository.savedNoteWithAttachments != null)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun restoreNoteEmitsRestored() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "Archived",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                        status = NoteStatus.ARCHIVE,
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("5", repository)

                assertTrue(viewModel.uiState.value is NoteDetailViewModel.UiState.NoteLoaded)

                viewModel.restoreNote()
                assertEquals(NoteDetailViewModel.UiState.Restored, viewModel.uiState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun restoreNoteEmitsErrorOnFailure() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "Archived",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                        status = NoteStatus.ARCHIVE,
                    )
                val repository = FakeNoteDetailRepository(note = note, shouldFailRestore = true)
                val viewModel = createViewModel("5", repository)

                viewModel.restoreNote()

                val state = viewModel.uiState.value
                assertTrue(state is NoteDetailViewModel.UiState.Error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun noteStatusReturnsCorrectStatus() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val note =
                    Note(
                        id = "5",
                        title = "Archived",
                        content = "Content",
                        createdAt = 1000L,
                        modifiedAt = 1000L,
                        tags = emptySet(),
                        status = NoteStatus.ARCHIVE,
                    )
                val repository = FakeNoteDetailRepository(note = note)
                val viewModel = createViewModel("5", repository)

                assertEquals(NoteStatus.ARCHIVE, viewModel.noteStatus())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun onRemoveAttachmentRemovesItFromList() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val attachment1 = Attachment("att-1", "note-1", "/path/img1.png", "image/png")
                val attachment2 = Attachment("att-2", "note-1", "/path/img2.png", "image/png")
                val note = Note("note-1", "Title", "Content", 1000L, 1000L, emptySet())
                val repository = FakeNoteDetailRepository(note = note, attachments = listOf(attachment1, attachment2))
                val viewModel = createViewModel("note-1", repository)

                viewModel.onRemoveAttachment(attachment1)

                assertEquals(1, viewModel.attachments.value.size)
                assertEquals("att-2", viewModel.attachments.value[0].id)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun savingWithRemovedAttachmentCallsRepositoryEvenIfTextUnchanged() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val attachment = Attachment("att-1", "note-1", "/path/img.png", "image/png")
                val note = Note("note-1", "Existing", "<b>Content</b>", 1000L, 1000L, emptySet())
                val repository = FakeNoteDetailRepository(note = note, attachments = listOf(attachment))
                val viewModel = createViewModel("note-1", repository)

                viewModel.onRemoveAttachment(attachment)
                // title and content unchanged — would normally short-circuit — but attachment changed
                viewModel.saveNote("Existing", "<b>Content</b>", "Content")

                assertEquals(NoteDetailViewModel.UiState.Saved, viewModel.uiState.value)
                val saved = repository.savedNoteWithAttachments
                assertTrue("Repository save should have been called", saved != null)
                assertTrue("Saved note should have no attachments", saved!!.attachments.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveDeletesLocalFileForRemovedAttachment() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val attachment = Attachment("att-to-delete", "note-1", "/path/img.png", "image/png")
                val note = Note("note-1", "Title", "Content", 1000L, 1000L, emptySet())
                val repository = FakeNoteDetailRepository(note = note, attachments = listOf(attachment))
                val storage = TrackingAttachmentStorage()
                val viewModel = createViewModel("note-1", repository, storage = storage)

                viewModel.onRemoveAttachment(attachment)
                viewModel.saveNote("Title", "Changed content", "Changed content")

                assertTrue(
                    "deleteFile should be called for the removed attachment",
                    storage.deletedIds.contains("att-to-delete"),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun saveDoesNotDeleteLocalFileForRetainedAttachment() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val attachment1 = Attachment("att-keep", "note-1", "/path/img1.png", "image/png")
                val attachment2 = Attachment("att-remove", "note-1", "/path/img2.png", "image/png")
                val note = Note("note-1", "Title", "Content", 1000L, 1000L, emptySet())
                val repository = FakeNoteDetailRepository(note = note, attachments = listOf(attachment1, attachment2))
                val storage = TrackingAttachmentStorage()
                val viewModel = createViewModel("note-1", repository, storage = storage)

                viewModel.onRemoveAttachment(attachment2)
                viewModel.saveNote("Title", "Changed content", "Changed content")

                assertEquals(listOf("att-remove"), storage.deletedIds)
                assertFalse("File for retained attachment must not be deleted", storage.deletedIds.contains("att-keep"))
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        noteId: String,
        repository: FakeNoteDetailRepository = FakeNoteDetailRepository(),
        isNewNote: Boolean = false,
        storage: AttachmentFileStorage = NoOpAttachmentStorage(),
    ): NoteDetailViewModel =
        NoteDetailViewModel(
            noteId = noteId,
            isNewNote = isNewNote,
            getNoteByIdUseCase = GetNoteByIdUseCase(repository),
            saveNoteUseCase = SaveNoteUseCase(repository),
            deleteNoteUseCase = DeleteNoteUseCase(repository),
            moveToTrashUseCase = MoveToTrashUseCase(repository),
            archiveNoteUseCase = ArchiveNoteUseCase(repository),
            restoreNoteUseCase = RestoreNoteUseCase(repository),
            createAttachment = CreateAttachmentUseCase(storage),
            // Keep the local file deletion on the test scheduler so its side effects are
            // observable synchronously and do not leak past the test onto a real IO thread.
            deleteAttachmentLocal = DeleteAttachmentLocalUseCase(storage, UnconfinedTestDispatcher()),
        )

    private open class NoOpAttachmentStorage : AttachmentFileStorage {
        override fun copyToStorage(
            source: String,
            attachmentId: String,
            mimeType: String?,
        ) = ""

        override fun writeStream(
            attachmentId: String,
            inputStream: InputStream,
        ) = ""

        override fun openWriteStream(
            attachmentId: String,
            extension: String?,
        ) = AttachmentWriteTarget("", ByteArrayOutputStream())

        override fun getFile(attachmentId: String) = File("")

        override fun deleteFile(attachmentId: String) {}
    }

    private class TrackingAttachmentStorage : NoOpAttachmentStorage() {
        val deletedIds = mutableListOf<String>()

        override fun deleteFile(attachmentId: String) {
            deletedIds.add(attachmentId)
        }
    }

    private class FakeNoteDetailRepository(
        private val note: Note? = null,
        private val attachments: List<Attachment> = emptyList(),
        private val shouldFailLoad: Boolean = false,
        private val shouldFailSave: Boolean = false,
        private val shouldFailDelete: Boolean = false,
        private val shouldFailMoveToTrash: Boolean = false,
        private val shouldFailArchive: Boolean = false,
        private val shouldFailRestore: Boolean = false,
    ) : BaseFakeNotesRepository() {
        var savedNoteWithAttachments: NoteWithAttachments? = null

        override suspend fun getNoteById(id: String): Result<NoteWithAttachments> {
            if (shouldFailLoad) return Result.Failure(RuntimeException("Load error"))
            if (note == null) {
                return Result.Success(NoteWithAttachments(Note(id, null, "", 0L, 0L, emptySet()), emptyList()))
            }
            return Result.Success(NoteWithAttachments(note, attachments))
        }

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> {
            if (shouldFailSave) return Result.Failure(RuntimeException("Save error"))
            savedNoteWithAttachments = noteWithAttachments
            return Result.Success(Unit)
        }

        override suspend fun deleteNote(id: String): Result<Unit> {
            if (shouldFailDelete) return Result.Failure(RuntimeException("Delete error"))
            return Result.Success(Unit)
        }

        override suspend fun moveToTrash(id: String): Result<Unit> {
            if (shouldFailMoveToTrash) return Result.Failure(RuntimeException("Trash error"))
            return Result.Success(Unit)
        }

        override suspend fun archiveNote(id: String): Result<Unit> {
            if (shouldFailArchive) return Result.Failure(RuntimeException("Archive error"))
            return Result.Success(Unit)
        }

        override suspend fun restoreNote(id: String): Result<Unit> {
            if (shouldFailRestore) return Result.Failure(RuntimeException("Restore error"))
            return Result.Success(Unit)
        }
    }
}
