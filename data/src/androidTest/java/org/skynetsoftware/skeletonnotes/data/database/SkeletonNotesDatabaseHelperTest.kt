package org.skynetsoftware.skeletonnotes.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CONTENT
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CREATED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TAGS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TITLE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES

@RunWith(AndroidJUnit4::class)
class SkeletonNotesDatabaseHelperTest {

    private lateinit var databaseHelper: SkeletonNotesDatabaseHelper
    private lateinit var database: SQLiteDatabase

    @Before
    fun setUp() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        databaseHelper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        database = databaseHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")
    }

    @Test
    fun databaseVersionIsOne() {
        assertEquals(1, database.version)
    }

    @Test
    fun notesTableExists() {
        val cursor = database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$TABLE_NOTES'",
            null
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(TABLE_NOTES, cursor.getString(0))
        cursor.close()
    }

    @Test
    fun attachmentsTableExists() {
        val cursor = database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$TABLE_ATTACHMENTS'",
            null
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(TABLE_ATTACHMENTS, cursor.getString(0))
        cursor.close()
    }

    @Test
    fun notesTableHasAllColumns() {
        val cursor = database.rawQuery("PRAGMA table_info($TABLE_NOTES)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue(columns.contains(COLUMN_ID))
        assertTrue(columns.contains(COLUMN_TITLE))
        assertTrue(columns.contains(COLUMN_CONTENT))
        assertTrue(columns.contains(COLUMN_CREATED))
        assertTrue(columns.contains(COLUMN_MODIFIED))
        assertTrue(columns.contains(COLUMN_TAGS))
    }

    @Test
    fun attachmentsTableHasAllColumns() {
        val cursor = database.rawQuery("PRAGMA table_info($TABLE_ATTACHMENTS)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue(columns.contains(COLUMN_ID))
        assertTrue(columns.contains(COLUMN_NOTE_ID))
        assertTrue(columns.contains(COLUMN_URI))
    }

    @Test
    fun onUpgradeDoesNotCrash() {
        database.close()
        databaseHelper.onUpgrade(database, 1, 2)
    }
}
