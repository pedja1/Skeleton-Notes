package org.skynetsoftware.skeletonnotes.data.database

import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import java.util.UUID

/**
 * SQLite database helper for the Skeleton Notes application.
 * Manages creation and upgrades of the notes and attachments tables.
 *
 * @param application the application context.
 * @param databaseName the database file name, or `null` for an in-memory database (used by
 * instrumented tests to avoid touching the on-disk database).
 */
internal class SkeletonNotesDatabaseHelper(
    application: Application,
    databaseName: String? = "skeleton-notes",
) : SQLiteOpenHelper(application, databaseName, null, 4) {

    companion object {
        // table notes
        const val TABLE_NOTES = "notes"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CREATED = "created"
        const val COLUMN_MODIFIED = "modified"
        const val COLUMN_TAGS = "tags"
        const val COLUMN_STATUS = "status"
        const val COLUMN_REMOTE_LAST_MODIFIED = "remoteLastModified"

        //table attachments
        const val TABLE_ATTACHMENTS = "attachments"
        const val COLUMN_NOTE_ID = "noteId"
        const val COLUMN_URI = "uri"
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE_NOTES` (
                `$COLUMN_ID` TEXT NOT NULL,
                `$COLUMN_TITLE` TEXT,
                `$COLUMN_CONTENT` TEXT NOT NULL,
                `$COLUMN_CREATED` INTEGER NOT NULL,
                `$COLUMN_MODIFIED` INTEGER NOT NULL,
                `$COLUMN_TAGS` TEXT,
                `$COLUMN_STATUS` INTEGER NOT NULL DEFAULT ${NoteStatus.ACTIVE.value},
                `$COLUMN_REMOTE_LAST_MODIFIED` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`$COLUMN_ID`)
            )
        """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE_ATTACHMENTS` (
                `$COLUMN_ID` TEXT NOT NULL,
                `$COLUMN_NOTE_ID` TEXT NOT NULL,
                `$COLUMN_URI` TEXT NOT NULL,
                PRIMARY KEY(`$COLUMN_ID`)
            )
        """.trimIndent()
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE `$TABLE_NOTES` ADD COLUMN `$COLUMN_STATUS` INTEGER NOT NULL DEFAULT ${NoteStatus.ACTIVE.value}")
        }
        if (oldVersion < 3) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `nextcloud_sync_mapping` (
                    `localNoteId` INTEGER NOT NULL,
                    `remoteNoteId` INTEGER NOT NULL,
                    `etag` TEXT NOT NULL,
                    PRIMARY KEY(`localNoteId`)
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            migrateToV4(database)
        }
    }

    private fun migrateToV4(database: SQLiteDatabase) {
        val idMapping = mutableMapOf<Long, String>()

        database.execSQL(
            """
            CREATE TABLE `notes_v4` (
                `$COLUMN_ID` TEXT NOT NULL,
                `$COLUMN_TITLE` TEXT,
                `$COLUMN_CONTENT` TEXT NOT NULL,
                `$COLUMN_CREATED` INTEGER NOT NULL,
                `$COLUMN_MODIFIED` INTEGER NOT NULL,
                `$COLUMN_TAGS` TEXT,
                `$COLUMN_STATUS` INTEGER NOT NULL DEFAULT ${NoteStatus.ACTIVE.value},
                `$COLUMN_REMOTE_LAST_MODIFIED` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`$COLUMN_ID`)
            )
        """.trimIndent()
        )

        database.execSQL(
            """
            CREATE TABLE `attachments_v4` (
                `$COLUMN_ID` TEXT NOT NULL,
                `$COLUMN_NOTE_ID` TEXT NOT NULL,
                `$COLUMN_URI` TEXT NOT NULL,
                PRIMARY KEY(`$COLUMN_ID`)
            )
        """.trimIndent()
        )

        database.rawQuery("SELECT $COLUMN_ID, $COLUMN_TITLE, $COLUMN_CONTENT, $COLUMN_CREATED, $COLUMN_MODIFIED, $COLUMN_TAGS, $COLUMN_STATUS FROM $TABLE_NOTES", null).use { cursor ->
            while (cursor.moveToNext()) {
                val oldId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val newId = UUID.randomUUID().toString()
                idMapping[oldId] = newId

                val values = ContentValues().apply {
                    put(COLUMN_ID, newId)
                    put(COLUMN_TITLE, cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_TITLE)))
                    put(COLUMN_CONTENT, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)))
                    put(COLUMN_CREATED, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED)))
                    put(COLUMN_MODIFIED, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MODIFIED)))
                    put(COLUMN_TAGS, cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_TAGS)))
                    put(COLUMN_STATUS, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS)))
                    put(COLUMN_REMOTE_LAST_MODIFIED, 0L)
                }
                database.insert("notes_v4", null, values)
            }
        }

        database.rawQuery("SELECT $COLUMN_ID, $COLUMN_NOTE_ID, $COLUMN_URI FROM $TABLE_ATTACHMENTS", null).use { cursor ->
            while (cursor.moveToNext()) {
                val oldAttachmentId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val oldNoteId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NOTE_ID))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URI))
                val newNoteId = idMapping[oldNoteId] ?: continue

                val values = ContentValues().apply {
                    put(COLUMN_ID, UUID.randomUUID().toString())
                    put(COLUMN_NOTE_ID, newNoteId)
                    put(COLUMN_URI, uri)
                }
                database.insert("attachments_v4", null, values)
            }
        }

        database.execSQL("DROP TABLE IF EXISTS `$TABLE_NOTES`")
        database.execSQL("ALTER TABLE `notes_v4` RENAME TO `$TABLE_NOTES`")
        database.execSQL("DROP TABLE IF EXISTS `$TABLE_ATTACHMENTS`")
        database.execSQL("ALTER TABLE `attachments_v4` RENAME TO `$TABLE_ATTACHMENTS`")
        database.execSQL("DROP TABLE IF EXISTS `nextcloud_sync_mapping`")
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
