package org.skynetsoftware.skeletonnotes.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSourceImpl
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

@RunWith(AndroidJUnit4::class)
class NotesRepositoryImplInstrumentedTest {

    private lateinit var repository: NotesRepositoryImpl

    @Before
    fun setUp() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val databaseHelper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        val dataSource = NotesDataSourceImpl(databaseHelper)
        repository = NotesRepositoryImpl(dataSource)
    }

    @After
    fun tearDown() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")
    }

    @Test
    fun saveAndRetrieveNote() = runBlocking {
        val note = Note(id = 0, title = "Test", content = "# Test\nContent", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val result = repository.saveNote(NoteWithAttachments(note = note, attachments = emptyList()))

        assertTrue(result is Result.Success)
        val id = (result as Result.Success).data
        assertTrue(id > 0)

        val retrieved = repository.getNoteById(id)
        assertTrue(retrieved is Result.Success)
        val retrievedNote = (retrieved as Result.Success).data
        assertEquals("Test", retrievedNote.note.title)
        assertEquals("# Test\nContent", retrievedNote.note.content)
    }

    @Test
    fun getAllNotesReturnsAllSavedNotes() = runBlocking {
        repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "A", content = "# A\nFirst", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "B", content = "# B\nSecond", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val result = repository.getAllNotes()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertEquals(2, notes.size)
    }

    @Test
    fun getAllNotesReturnsEmptyListWhenNoNotes() = runBlocking {
        val result = repository.getAllNotes()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertTrue(notes.isEmpty())
    }

    @Test
    fun updateNotePersistsChanges() = runBlocking {
        val saveResult = repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "Original", content = "# Original\nOld", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        val id = (saveResult as Result.Success).data

        repository.saveNote(NoteWithAttachments(
            note = Note(id = id, title = "Updated", content = "# Updated\nNew", createdAt = 1000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val retrieved = repository.getNoteById(id)
        val note = (retrieved as Result.Success).data
        assertEquals("Updated", note.note.title)
        assertEquals("# Updated\nNew", note.note.content)
    }

    @Test
    fun deleteNoteRemovesIt() = runBlocking {
        val saveResult = repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "Del", content = "# Del\nRemove", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        val id = (saveResult as Result.Success).data

        val deleteResult = repository.deleteNote(id)
        assertTrue(deleteResult is Result.Success)

        val allNotes = repository.getAllNotes()
        val notes = (allNotes as Result.Success).data
        assertTrue(notes.none { it.id == id })
    }

    @Test
    fun deleteNonExistentNoteDoesNotThrow() = runBlocking {
        val result = repository.deleteNote(999L)

        assertTrue(result is Result.Success)
    }

    @Test
    fun getNoteByIdForNonExistentNoteThrowsInDataSource() = runBlocking {
        val result = repository.getNoteById(999L)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun saveNoteWithAttachmentsStoresAttachments() = runBlocking {
        val note = Note(id = 0, title = "With Attachments", content = "# Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val attachments = listOf(
            Attachment(id = 0, noteId = 0, uri = "file://photo.jpg"),
            Attachment(id = 0, noteId = 0, uri = "file://audio.mp3")
        )
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = attachments)

        val saveResult = repository.saveNote(noteWithAttachments)
        val id = (saveResult as Result.Success).data

        val retrieved = repository.getNoteById(id)
        val retrievedNoteWithAttachments = (retrieved as Result.Success).data
        assertEquals("With Attachments", retrievedNoteWithAttachments.note.title)
        assertEquals(2, retrievedNoteWithAttachments.attachments.size)
        assertEquals("file://photo.jpg", retrievedNoteWithAttachments.attachments[0].uri)
        assertEquals("file://audio.mp3", retrievedNoteWithAttachments.attachments[1].uri)
    }

    @Test
    fun notesAreSortedByModifiedAtDescending() = runBlocking {
        repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "Older", content = "# Older", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        repository.saveNote(NoteWithAttachments(
            note = Note(id = 0, title = "Newer", content = "# Newer", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val result = repository.getAllNotes()
        val notes = (result as Result.Success).data

        assertEquals("Newer", notes[0].title)
        assertEquals("Older", notes[1].title)
    }
}
