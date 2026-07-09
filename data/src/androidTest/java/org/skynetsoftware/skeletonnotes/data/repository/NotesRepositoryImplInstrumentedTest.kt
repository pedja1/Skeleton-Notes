package org.skynetsoftware.skeletonnotes.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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
    fun saveAndRetrieveNote() = runTest(timeout = 5.seconds) {
        val noteId = UUID.randomUUID().toString()
        val note = Note(id = noteId, title = "Test", content = "# Test\nContent", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        repository.saveNote(NoteWithAttachments(note = note, attachments = emptyList()))

        val retrieved = repository.getNoteById(noteId)
        assertTrue(retrieved is Result.Success)
        val retrievedNote = (retrieved as Result.Success).data
        assertEquals("Test", retrievedNote.note.title)
        assertEquals("# Test\nContent", retrievedNote.note.content)
    }

    @Test
    fun getAllNotesReturnsAllSavedNotesFlow() = runTest(timeout = 5.seconds) {
        repository.saveNote(NoteWithAttachments(
            note = Note(id = UUID.randomUUID().toString(), title = "A", content = "# A\nFirst", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        repository.saveNote(NoteWithAttachments(
            note = Note(id = UUID.randomUUID().toString(), title = "B", content = "# B\nSecond", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val result = repository.getAllNotesFlow().first()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertEquals(2, notes.size)
    }

    @Test
    fun getAllNotesReturnsEmptyListWhenNoNotesFlow() = runTest(timeout = 5.seconds) {
        val result = repository.getAllNotesFlow().first()

        assertTrue(result is Result.Success)
        val notes = (result as Result.Success).data
        assertTrue(notes.isEmpty())
    }

    @Test
    fun updateNotePersistsChanges() = runTest(timeout = 5.seconds) {
        val noteId = UUID.randomUUID().toString()
        repository.saveNote(NoteWithAttachments(
            note = Note(id = noteId, title = "Original", content = "# Original\nOld", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))

        repository.saveNote(NoteWithAttachments(
            note = Note(id = noteId, title = "Updated", content = "# Updated\nNew", createdAt = 1000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val retrieved = repository.getNoteById(noteId)
        val note = (retrieved as Result.Success).data
        assertEquals("Updated", note.note.title)
        assertEquals("# Updated\nNew", note.note.content)
    }

    @Test
    fun deleteNoteRemovesIt() = runTest(timeout = 5.seconds) {
        val noteId = UUID.randomUUID().toString()
        repository.saveNote(NoteWithAttachments(
            note = Note(id = noteId, title = "Del", content = "# Del\nRemove", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val deleteResult = repository.deleteNote(noteId)
        assertTrue(deleteResult is Result.Success)

        val allNotes = repository.getAllNotesFlow().first()
        val notes = (allNotes as Result.Success).data
        assertTrue(notes.none { it.id == noteId })
    }

    @Test
    fun deleteNonExistentNoteDoesNotThrow() = runTest(timeout = 5.seconds) {
        val result = repository.deleteNote("nonexistent")

        assertTrue(result is Result.Success)
    }

    @Test
    fun getNoteByIdForNonExistentNoteThrowsInDataSource() = runTest(timeout = 5.seconds) {
        val result = repository.getNoteById("nonexistent")

        assertTrue(result is Result.Failure)
    }

    @Test
    fun saveNoteWithAttachmentsStoresAttachments() = runTest(timeout = 5.seconds) {
        val noteId = UUID.randomUUID().toString()
        val note = Note(id = noteId, title = "With Attachments", content = "# Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val attachments = listOf(
            Attachment(id = UUID.randomUUID().toString(), noteId = noteId, uri = "file://photo.jpg"),
            Attachment(id = UUID.randomUUID().toString(), noteId = noteId, uri = "file://audio.mp3")
        )
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = attachments)

        repository.saveNote(noteWithAttachments)

        val retrieved = repository.getNoteById(noteId)
        val retrievedNoteWithAttachments = (retrieved as Result.Success).data
        assertEquals("With Attachments", retrievedNoteWithAttachments.note.title)
        assertEquals(2, retrievedNoteWithAttachments.attachments.size)
        assertEquals("file://photo.jpg", retrievedNoteWithAttachments.attachments[0].uri)
        assertEquals("file://audio.mp3", retrievedNoteWithAttachments.attachments[1].uri)
    }

    @Test
    fun notesAreSortedByModifiedAtDescending() = runTest(timeout = 5.seconds) {
        repository.saveNote(NoteWithAttachments(
            note = Note(id = UUID.randomUUID().toString(), title = "Older", content = "# Older", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            attachments = emptyList()
        ))
        repository.saveNote(NoteWithAttachments(
            note = Note(id = UUID.randomUUID().toString(), title = "Newer", content = "# Newer", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet()),
            attachments = emptyList()
        ))

        val result = repository.getAllNotesFlow().first()
        val notes = (result as Result.Success).data

        assertEquals("Newer", notes[0].title)
        assertEquals("Older", notes[1].title)
    }
}
