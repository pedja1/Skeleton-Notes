package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

class NoteRepositoryImplTest {

    private lateinit var dataSource: FakeNotesDataSource
    private lateinit var repository: NotesRepositoryImpl

    @Before
    fun setUp() {
        dataSource = FakeNotesDataSource()
        repository = NotesRepositoryImpl(dataSource)
    }

    @Test
    fun saveNoteReturnsId() = runBlocking {
        val note = Note(id = 0, title = "Test", content = "# Test\nContent", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = emptyList())

        val result = repository.saveNote(noteWithAttachments)

        assertTrue(result is Result.Success)
        val id = (result as Result.Success).data
        assertEquals(1L, id)
    }

    @Test
    fun saveNotePassesCorrectDataToDataSource() = runBlocking {
        val note = Note(id = 0, title = "Title", content = "Content", createdAt = 1000L, modifiedAt = 2000L, tags = setOf("tag1"))
        val attachment = Attachment(id = 0, noteId = 0, uri = "file://test")
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = listOf(attachment))

        repository.saveNote(noteWithAttachments)

        assertEquals(1, dataSource.savedNotes.size)
        assertEquals("Title", dataSource.savedNotes[0].note.title)
        assertEquals("Content", dataSource.savedNotes[0].note.content)
        assertEquals(1, dataSource.savedNotes[0].attachments.size)
    }

    @Test
    fun saveNotePropagatesFailure() = runBlocking {
        dataSource.shouldFail = true
        val note = Note(id = 0, title = "Test", content = "Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())

        val result = repository.saveNote(NoteWithAttachments(note = note, attachments = emptyList()))

        assertTrue(result is Result.Failure)
    }

    @Test
    fun getAllNotesReturnsAllSavedNotes() = runBlocking {
        val note1 = Note(id = 1, title = "A", content = "# A\nFirst", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val note2 = Note(id = 2, title = "B", content = "# B\nSecond", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet())
        dataSource.notes = listOf(note1, note2)

        val result = repository.getAllNotes()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertEquals(2, notes.size)
    }

    @Test
    fun getAllNotesReturnsEmptyListWhenDataSourceEmpty() = runBlocking {
        dataSource.notes = emptyList()

        val result = repository.getAllNotes()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertTrue(notes.isEmpty())
    }

    @Test
    fun getAllNotesPropagatesFailure() = runBlocking {
        dataSource.shouldFail = true

        val result = repository.getAllNotes()

        assertTrue(result is Result.Failure)
    }

    @Test
    fun getNoteByIdReturnsCorrectNote() = runBlocking {
        val note = Note(id = 1, title = "Target", content = "# Target\nContent", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        dataSource.noteById = NoteWithAttachments(note = note, attachments = emptyList())

        val result = repository.getNoteById(1L)

        assertTrue(result is Result.Success)
        val retrieved = (result as Result.Success).data
        assertEquals(1L, retrieved.note.id)
        assertEquals("Target", retrieved.note.title)
        assertEquals("# Target\nContent", retrieved.note.content)
    }

    @Test
    fun getNoteByIdPropagatesFailure() = runBlocking {
        dataSource.shouldFail = true

        val result = repository.getNoteById(1L)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun deleteNoteSucceeds() = runBlocking {
        val result = repository.deleteNote(1L)

        assertTrue(result is Result.Success)
        assertEquals(1L, dataSource.deletedNoteId)
    }

    @Test
    fun deleteNotePropagatesFailure() = runBlocking {
        dataSource.shouldFail = true

        val result = repository.deleteNote(1L)

        assertTrue(result is Result.Failure)
    }

    private class FakeNotesDataSource : NotesDataSource {

        var notes: List<Note> = emptyList()
        var noteById: NoteWithAttachments = NoteWithAttachments(
            note = Note(id = 1, title = "", content = "", createdAt = 0L, modifiedAt = 0L, tags = emptySet()),
            attachments = emptyList()
        )
        val savedNotes = mutableListOf<NoteWithAttachments>()
        var deletedNoteId: Long = -1L
        var shouldFail = false

        override suspend fun getAllNotes(): Result<List<Note>> {
            if (shouldFail) return Result.Failure(RuntimeException("test failure"))
            return Result.Success(notes)
        }

        override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> {
            if (shouldFail) return Result.Failure(RuntimeException("test failure"))
            return Result.Success(noteById)
        }

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> {
            if (shouldFail) return Result.Failure(RuntimeException("test failure"))
            savedNotes.add(noteWithAttachments)
            return Result.Success((savedNotes.size).toLong())
        }

        override suspend fun deleteNote(id: Long): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("test failure"))
            deletedNoteId = id
            return Result.Success(Unit)
        }
    }
}
