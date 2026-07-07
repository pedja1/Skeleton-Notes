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
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_STATUS
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
    fun databaseVersionIsTwo() {
        assertEquals(2, database.version)
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
        assertTrue(columns.contains(COLUMN_STATUS))
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
    fun migrateFromV1ToV2AddsStatusColumn() {
        database.close()
        databaseHelper.close()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")

        val v1Db = context.openOrCreateDatabase("skeleton-notes", 0, null)
        v1Db.version = 1
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_TITLE TEXT,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_CREATED INTEGER NOT NULL,
                $COLUMN_MODIFIED INTEGER NOT NULL,
                $COLUMN_TAGS TEXT,
                PRIMARY KEY($COLUMN_ID)
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            "INSERT INTO $TABLE_NOTES ($COLUMN_ID, $COLUMN_TITLE, $COLUMN_CONTENT, $COLUMN_CREATED, $COLUMN_MODIFIED) " +
                "VALUES (1, 'Test', 'Content', 1000, 1000)"
        )
        v1Db.close()

        val v2Helper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        val v2Db = v2Helper.writableDatabase

        val cursor = v2Db.rawQuery("PRAGMA table_info($TABLE_NOTES)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue(columns.contains(COLUMN_STATUS))

        val dataCursor = v2Db.rawQuery("SELECT $COLUMN_STATUS FROM $TABLE_NOTES WHERE $COLUMN_ID = 1", null)
        assertTrue(dataCursor.moveToFirst())
        assertEquals(0, dataCursor.getInt(0))
        dataCursor.close()

        v2Db.close()
        v2Helper.close()
        context.deleteDatabase("skeleton-notes")
    }

    @Test
    fun onUpgradeDoesNotCrash() {
        database.close()
        databaseHelper.close()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")

        val v1Db = context.openOrCreateDatabase("skeleton-notes", 0, null)
        v1Db.version = 1
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_TITLE TEXT,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_CREATED INTEGER NOT NULL,
                $COLUMN_MODIFIED INTEGER NOT NULL,
                $COLUMN_TAGS TEXT,
                PRIMARY KEY($COLUMN_ID)
            )
            """.trimIndent()
        )
        v1Db.close()

        val helper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        val upgradedDb = helper.writableDatabase
        upgradedDb.close()
        helper.close()
        context.deleteDatabase("skeleton-notes")
    }
}
