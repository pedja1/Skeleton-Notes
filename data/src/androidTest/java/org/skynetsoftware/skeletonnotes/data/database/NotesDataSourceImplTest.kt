package org.skynetsoftware.skeletonnotes.data.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

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
    fun getAllNotesReturnsFailureWhenDatabaseCorrupted() = runBlocking {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.getAllNotes()
        assertTrue(result is Result.Failure)
    }

    @Test
    fun getNoteByIdReturnsFailureWhenDatabaseCorrupted() = runBlocking {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.getNoteById(1L)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun saveNoteReturnsFailureWhenDatabaseCorrupted() = runBlocking {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val note = Note(id = 0, title = "Test", content = "Content", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet())
        val noteWithAttachments = NoteWithAttachments(note = note, attachments = emptyList())
        val result = dataSource.saveNote(noteWithAttachments)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun deleteNoteReturnsFailureWhenDatabaseCorrupted() = runBlocking {
        database.execSQL("DROP TABLE IF EXISTS ${SkeletonNotesDatabaseHelper.TABLE_NOTES}")
        val dataSource = NotesDataSourceImpl(databaseHelper)
        val result = dataSource.deleteNote(1L)
        assertTrue(result is Result.Failure)
    }
}
