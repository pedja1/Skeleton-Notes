package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.skynetsoftware.skeletonnotes.domain.model.Note
import java.io.File

class NoteRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var notesDir: File
    private lateinit var repository: NotesRepositoryImpl

    @Before
    fun setUp() {
        notesDir = tempFolder.newFolder("notes")
        repository = NotesRepositoryImpl(notesDir)
    }

    @Test
    fun saveNewNoteCreatesFileWithCorrectContent() = runBlocking {
        val note = Note(title = "Test", content = "# Test\nSome content")
        val id = repository.saveNote(note)

        val file = File(notesDir, "$id.md")
        assertTrue(file.exists())
        assertEquals("# Test\nSome content", file.readText())
    }

    @Test
    fun saveNoteSetsFileLastModifiedToCreatedAt() = runBlocking {
        val timestamp = 1000L
        val note = Note(id = 0, title = "Test", content = "# Test\nContent", createdAt = timestamp)
        val id = repository.saveNote(note)

        val file = File(notesDir, "$id.md")
        assertEquals(timestamp, file.lastModified())
    }

    @Test
    fun firstSavedNoteGetsId1() = runBlocking {
        val note = Note(title = "First", content = "# First\nHello")
        val id = repository.saveNote(note)

        assertEquals(1L, id)
    }

    @Test
    fun savingMultipleNotesAssignsSequentialIds() = runBlocking {
        val id1 = repository.saveNote(Note(title = "A", content = "# A\nFirst"))
        val id2 = repository.saveNote(Note(title = "B", content = "# B\nSecond"))
        val id3 = repository.saveNote(Note(title = "C", content = "# C\nThird"))

        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
    }

    @Test
    fun saveUpdatesExistingNoteInPlace() = runBlocking {
        val note = Note(title = "Original", content = "# Original\nOld content")
        val id = repository.saveNote(note)

        val updated = note.copy(id = id, title = "Updated", content = "# Updated\nNew content")
        repository.saveNote(updated)

        val file = File(notesDir, "$id.md")
        assertEquals("# Updated\nNew content", file.readText())
    }

    @Test
    fun getAllNotesReturnsAllSavedNotes() = runBlocking {
        repository.saveNote(Note(title = "A", content = "# A\nFirst"))
        repository.saveNote(Note(title = "B", content = "# B\nSecond"))

        val notes = repository.getAllNotes()

        assertEquals(2, notes.size)
    }

    @Test
    fun getAllNotesReturnsNotesSortedByCreatedAtDescending() = runBlocking {
        val note1 = Note(title = "Older", content = "# Older", createdAt = 1000L)
        val note2 = Note(title = "Newer", content = "# Newer", createdAt = 2000L)

        repository.saveNote(note1)
        repository.saveNote(note2)

        val notes = repository.getAllNotes()
        assertEquals("Newer", notes[0].title)
        assertEquals("Older", notes[1].title)
    }

    @Test
    fun getNoteByIdReturnsCorrectNote() = runBlocking {
        val id = repository.saveNote(Note(title = "Target", content = "# Target\nContent"))

        val retrieved = repository.getNoteById(id)

        assertNotNull(retrieved)
        val note = retrieved!!
        assertEquals(id, note.id)
        assertEquals("Target", note.title)
        assertEquals("# Target\nContent", note.content)
    }

    @Test
    fun getNoteByIdReturnsNullForNonExistentId() = runBlocking {
        val result = repository.getNoteById(999L)
        assertNull(result)
    }

    @Test
    fun deleteRemovesFile() = runBlocking {
        val note = Note(title = "ToDelete", content = "# ToDelete\nGone")
        val id = repository.saveNote(note)

        repository.deleteNote(note.copy(id = id))

        val file = File(notesDir, "$id.md")
        assertTrue(!file.exists())
    }

    @Test
    fun deleteNonExistentNoteDoesNotThrow() = runBlocking {
        repository.deleteNote(Note(id = 999, title = "X", content = "# X"))
        // should not throw
    }

    @Test
    fun titleExtractedFromH1Heading() = runBlocking {
        val note = Note(title = "ignored", content = "# My Title\nSome text")
        val id = repository.saveNote(note)

        val retrieved = repository.getNoteById(id)
        assertEquals("My Title", retrieved?.title)
    }

    @Test
    fun titleExtractedFromFirstHeadingWhenMultipleHeadings() = runBlocking {
        val note = Note(title = "ignored", content = "# First Title\n## Second Title\ncontent")
        val id = repository.saveNote(note)

        val retrieved = repository.getNoteById(id)
        assertEquals("First Title", retrieved?.title)
    }

    @Test
    fun titleDefaultsToUntitledWhenNoHeading() = runBlocking {
        val note = Note(title = "ignored", content = "Just some content\nno heading here")
        val id = repository.saveNote(note)

        val retrieved = repository.getNoteById(id)
        assertEquals("Untitled", retrieved?.title)
    }

    @Test
    fun getAllNotesReturnsEmptyListForEmptyDirectory() = runBlocking {
        val notes = repository.getAllNotes()
        assertTrue(notes.isEmpty())
    }

    @Test
    fun savePreservesNoteIdOnUpdate() = runBlocking {
        val original = Note(title = "Original", content = "# Original\nContent")
        val id = repository.saveNote(original)

        val updated = original.copy(id = id, content = "# Original\nModified content")
        val returnedId = repository.saveNote(updated)

        assertEquals(id, returnedId)
        val notes = repository.getAllNotes()
        assertEquals(1, notes.size)
    }
}
