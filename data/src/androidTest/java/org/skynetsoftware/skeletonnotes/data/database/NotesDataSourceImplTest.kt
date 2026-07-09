package org.skynetsoftware.skeletonnotes.data.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class NotesDataSourceImplTest {

    private lateinit var databaseHelper: SkeletonNotesDatabaseHelper
    private lateinit var database: SQLiteDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseHelper = SkeletonNotesDatabaseHelper(context.applicationContext as Application)
        database = databaseHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")
    }

    @Test
    fun saveNoteThenGetNoteByIdReturnsSavedNote() = runTest(timeout = 5.seconds) {
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val id = UUID.randomUUID().toString()
        val note = Note(id = id, title = "Title", content = "Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())

        val saveResult = dataSource.saveNote(NoteWithAttachments(note, emptyList()))
        assertTrue(saveResult is Result.Success)

        val getResult = dataSource.getNoteById(id)
        assertTrue(getResult is Result.Success)
        val loaded = (getResult as Result.Success).data.note
        assertEquals(id, loaded.id)
        assertEquals("Title", loaded.title)
        assertEquals("Content", loaded.content)
    }

    @Test
    fun getAllNotesFlowReturnsSavedNotes() = runTest(timeout = 5.seconds) {
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val first = Note(id = UUID.randomUUID().toString(), title = "First", content = "One", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val second = Note(id = UUID.randomUUID().toString(), title = "Second", content = "Two", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet())
        dataSource.saveNote(NoteWithAttachments(first, emptyList()))
        dataSource.saveNote(NoteWithAttachments(second, emptyList()))

        val result = dataSource.getAllNotesFlow().first()
        assertTrue(result is Result.Success)
        val ids = (result as Result.Success).data.map { it.id }
        assertTrue(ids.contains(first.id))
        assertTrue(ids.contains(second.id))
    }

    @Test
    fun saveNoteWithExistingIdUpdatesNote() = runTest(timeout = 5.seconds) {
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val id = UUID.randomUUID().toString()
        dataSource.saveNote(
            NoteWithAttachments(
                Note(id = id, title = "Original", content = "Original content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
                emptyList()
            )
        )
        dataSource.saveNote(
            NoteWithAttachments(
                Note(id = id, title = "Updated", content = "Updated content", createdAt = 1000L, modifiedAt = 2000L, tags = emptySet()),
                emptyList()
            )
        )

        val result = dataSource.getNoteById(id)
        assertTrue(result is Result.Success)
        assertEquals("Updated", (result as Result.Success).data.note.title)

        val all = dataSource.getAllNotesFlow().first()
        assertEquals(1, ((all as Result.Success).data.filter { it.id == id }).size)
    }

    @Test
    fun deleteNoteRemovesIt() = runTest(timeout = 5.seconds) {
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val id = UUID.randomUUID().toString()
        dataSource.saveNote(
            NoteWithAttachments(
                Note(id = id, title = "ToDelete", content = "Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
                emptyList()
            )
        )

        val deleteResult = dataSource.deleteNote(id)
        assertTrue(deleteResult is Result.Success)

        val all = dataSource.getAllNotesFlow().first()
        assertTrue((all as Result.Success).data.none { it.id == id })
    }

    @Test
    fun getAllNotesFlowReturnsFailureWhenDatabaseCorrupted() = runTest(timeout = 5.seconds) {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.getAllNotesFlow().first()
        assertTrue(result is Result.Failure)
    }

    @Test
    fun getNoteByIdReturnsFailureWhenDatabaseCorrupted() = runTest(timeout = 5.seconds) {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.getNoteById("nonexistent")
        assertTrue(result is Result.Failure)
    }

    @Test
    fun saveNoteReturnsFailureWhenDatabaseCorrupted() = runTest(timeout = 5.seconds) {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val note = Note(id = "", title = "Test", content = "Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = emptyList())
        val result = dataSource.saveNote(noteWithAttachments)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun deleteNoteReturnsFailureWhenDatabaseCorrupted() = runTest(timeout = 5.seconds) {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.deleteNote("nonexistent")
        assertTrue(result is Result.Failure)
    }
}
