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
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_REMOTE_LAST_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_STATUS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TAGS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TITLE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES
import java.util.UUID

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
    fun databaseVersionIsFour() {
        assertEquals(4, database.version)
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
        assertTrue(columns.contains(COLUMN_REMOTE_LAST_MODIFIED))
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
    fun migrateFromV1ToV4AddsStatusColumnAndPreservesData() {
        database.close()
        databaseHelper.close()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")

        // Seed a realistic v1 database. v1 shipped with both a notes and an attachments table
        // (see the original SkeletonNotesDatabaseHelper.onCreate) but without a status column.
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
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ATTACHMENTS (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_NOTE_ID INTEGER NOT NULL,
                $COLUMN_URI TEXT NOT NULL,
                PRIMARY KEY($COLUMN_ID)
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            "INSERT INTO $TABLE_NOTES ($COLUMN_ID, $COLUMN_TITLE, $COLUMN_CONTENT, $COLUMN_CREATED, $COLUMN_MODIFIED) " +
                "VALUES (1, 'Test', 'Content', 1000, 1000)"
        )
        v1Db.close()

        // Opening the helper upgrades all the way to v4 (status column added along the way, then
        // ids rewritten to UUIDs).
        val v4Helper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        val v4Db = v4Helper.writableDatabase

        assertTrue(tableColumns(v4Db, TABLE_NOTES).contains(COLUMN_STATUS))

        // The note survived the migration with a default status and a freshly generated UUID id.
        val dataCursor = v4Db.rawQuery(
            "SELECT $COLUMN_ID, $COLUMN_STATUS FROM $TABLE_NOTES WHERE $COLUMN_TITLE = 'Test'",
            null
        )
        assertTrue(dataCursor.moveToFirst())
        val migratedId = dataCursor.getString(0)
        assertEquals(0, dataCursor.getInt(1))
        dataCursor.close()
        assertEquals(migratedId, UUID.fromString(migratedId).toString())

        v4Db.close()
        v4Helper.close()
        context.deleteDatabase("skeleton-notes")
    }

    @Test
    fun migrateFromV3ToV4RewritesIntegerIdsToUuids() {
        database.close()
        databaseHelper.close()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("skeleton-notes")

        // Seed a v3 database: INTEGER ids, a status column, integer attachment/note ids and the
        // (soon-to-be-dropped) nextcloud_sync_mapping table.
        val v3Db = context.openOrCreateDatabase("skeleton-notes", 0, null)
        v3Db.version = 3
        v3Db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_TITLE TEXT,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_CREATED INTEGER NOT NULL,
                $COLUMN_MODIFIED INTEGER NOT NULL,
                $COLUMN_TAGS TEXT,
                $COLUMN_STATUS INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY($COLUMN_ID)
            )
            """.trimIndent()
        )
        v3Db.execSQL(
            """
            CREATE TABLE $TABLE_ATTACHMENTS (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_NOTE_ID INTEGER NOT NULL,
                $COLUMN_URI TEXT NOT NULL,
                PRIMARY KEY($COLUMN_ID)
            )
            """.trimIndent()
        )
        v3Db.execSQL(
            """
            CREATE TABLE nextcloud_sync_mapping (
                localNoteId INTEGER NOT NULL,
                remoteNoteId INTEGER NOT NULL,
                etag TEXT NOT NULL,
                PRIMARY KEY(localNoteId)
            )
            """.trimIndent()
        )
        v3Db.execSQL(
            "INSERT INTO $TABLE_NOTES ($COLUMN_ID, $COLUMN_TITLE, $COLUMN_CONTENT, $COLUMN_CREATED, $COLUMN_MODIFIED, $COLUMN_TAGS, $COLUMN_STATUS) " +
                "VALUES (1, 'Note One', 'Content One', 1000, 1500, '#a', 1)"
        )
        v3Db.execSQL(
            "INSERT INTO $TABLE_NOTES ($COLUMN_ID, $COLUMN_TITLE, $COLUMN_CONTENT, $COLUMN_CREATED, $COLUMN_MODIFIED, $COLUMN_TAGS, $COLUMN_STATUS) " +
                "VALUES (2, 'Note Two', 'Content Two', 2000, 2500, NULL, 0)"
        )
        v3Db.execSQL(
            "INSERT INTO $TABLE_ATTACHMENTS ($COLUMN_ID, $COLUMN_NOTE_ID, $COLUMN_URI) " +
                "VALUES (10, 1, 'content://media/photo.png')"
        )
        v3Db.execSQL("INSERT INTO nextcloud_sync_mapping (localNoteId, remoteNoteId, etag) VALUES (1, 100, 'etag')")
        v3Db.close()

        val v4Helper = SkeletonNotesDatabaseHelper(context.applicationContext as android.app.Application)
        val v4Db = v4Helper.writableDatabase

        // id columns are now TEXT.
        assertEquals("TEXT", columnType(v4Db, TABLE_NOTES, COLUMN_ID))
        assertEquals("TEXT", columnType(v4Db, TABLE_ATTACHMENTS, COLUMN_ID))
        assertEquals("TEXT", columnType(v4Db, TABLE_ATTACHMENTS, COLUMN_NOTE_ID))

        // remoteLastModified column exists and defaults to 0 for migrated notes.
        val notesColumns = tableColumns(v4Db, TABLE_NOTES)
        assertTrue(notesColumns.contains(COLUMN_REMOTE_LAST_MODIFIED))

        // Note data is preserved and ids became UUIDs (no longer the old integers).
        val noteOneCursor = v4Db.rawQuery(
            "SELECT $COLUMN_ID, $COLUMN_CONTENT, $COLUMN_STATUS, $COLUMN_REMOTE_LAST_MODIFIED FROM $TABLE_NOTES WHERE $COLUMN_TITLE = 'Note One'",
            null
        )
        assertTrue(noteOneCursor.moveToFirst())
        val noteOneId = noteOneCursor.getString(0)
        assertEquals("Content One", noteOneCursor.getString(1))
        assertEquals(1, noteOneCursor.getInt(2))
        assertEquals(0L, noteOneCursor.getLong(3))
        noteOneCursor.close()

        assertEquals(noteOneId, UUID.fromString(noteOneId).toString())

        // Two notes survived the migration.
        val countCursor = v4Db.rawQuery("SELECT COUNT(*) FROM $TABLE_NOTES", null)
        assertTrue(countCursor.moveToFirst())
        assertEquals(2, countCursor.getInt(0))
        countCursor.close()

        // The attachment's noteId was remapped to the new UUID of its original note.
        val attachmentCursor = v4Db.rawQuery(
            "SELECT $COLUMN_ID, $COLUMN_NOTE_ID FROM $TABLE_ATTACHMENTS WHERE $COLUMN_URI = 'content://media/photo.png'",
            null
        )
        assertTrue(attachmentCursor.moveToFirst())
        val attachmentId = attachmentCursor.getString(0)
        assertEquals(noteOneId, attachmentCursor.getString(1))
        attachmentCursor.close()
        assertEquals(attachmentId, UUID.fromString(attachmentId).toString())

        // The transient sync-mapping table was dropped.
        val mappingCursor = v4Db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='nextcloud_sync_mapping'",
            null
        )
        assertTrue(!mappingCursor.moveToFirst())
        mappingCursor.close()

        v4Db.close()
        v4Helper.close()
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
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ATTACHMENTS (
                $COLUMN_ID INTEGER NOT NULL,
                $COLUMN_NOTE_ID INTEGER NOT NULL,
                $COLUMN_URI TEXT NOT NULL,
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

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        return columns
    }

    private fun columnType(db: SQLiteDatabase, table: String, column: String): String? {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) {
                    return cursor.getString(cursor.getColumnIndexOrThrow("type"))
                }
            }
        }
        return null
    }
}
