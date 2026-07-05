package org.skynetsoftware.skeletonnotes.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.domain.model.Note
import java.io.File

@RunWith(AndroidJUnit4::class)
class NotesRepositoryImplTest {

    private lateinit var notesDir: File
    private lateinit var repository: NotesRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        notesDir = File(context.filesDir, "notes")
        repository = NotesRepositoryImpl(notesDir)
    }

    @After
    fun tearDown() {
        notesDir.deleteRecursively()
    }

    @Test
    fun saveAndRetrieveNoteUsingRealFilesDir() = runBlocking {
        val note = Note(title = "Android Test", content = "# Android Test\nContent")
        val id = repository.saveNote(note)

        val retrieved = repository.getNoteById(id)
        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
        assertEquals("Android Test", retrieved?.title)
        assertEquals("# Android Test\nContent", retrieved?.content)
    }

    @Test
    fun saveMultipleNotesAndRetrieveAll() = runBlocking {
        repository.saveNote(Note(title = "First", content = "# First\nOne"))
        repository.saveNote(Note(title = "Second", content = "# Second\nTwo"))

        val notes = repository.getAllNotes()
        assertEquals(2, notes.size)
    }

    @Test
    fun updateNotePersistsChanges() = runBlocking {
        val id = repository.saveNote(Note(title = "Before", content = "# Before\nOld"))

        repository.saveNote(Note(id = id, title = "After", content = "# After\nNew"))

        val updated = repository.getNoteById(id)
        assertEquals("After", updated?.title)
        assertEquals("# After\nNew", updated?.content)
    }

    @Test
    fun deleteNoteRemovesFromFilesDir() = runBlocking {
        val id = repository.saveNote(Note(title = "Del", content = "# Del\nRemove me"))

        repository.deleteNote(Note(id = id, title = "Del", content = "# Del\nRemove me"))

        val file = File(notesDir, "$id.md")
        assertTrue(!file.exists())
    }

    @Test
    fun emptyDirectoryReturnsEmptyList() = runBlocking {
        val notes = repository.getAllNotes()
        assertTrue(notes.isEmpty())
    }
}
