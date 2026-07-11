package org.skynetsoftware.skeletonnotes.data.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

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
) : SQLiteOpenHelper(application, databaseName, null, DATABASE_VERSION) {
    init {
        // WAL lets readers run concurrently with a writer (e.g. a large import), so reads no longer
        // block on the single write connection. In-memory databases (used by tests) don't support WAL.
        if (databaseName != null) {
            setWriteAheadLoggingEnabled(true)
        }
    }

    companion object {
        private const val DATABASE_VERSION = 3

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

        // table attachments
        const val TABLE_ATTACHMENTS = "attachments"
        const val COLUMN_NOTE_ID = "noteId"
        const val COLUMN_URI = "uri"
        const val COLUMN_MIME_TYPE = "mimeType"
        const val COLUMN_FILENAME = "filename"
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
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE_ATTACHMENTS` (
                `$COLUMN_ID` TEXT NOT NULL,
                `$COLUMN_NOTE_ID` TEXT NOT NULL,
                `$COLUMN_URI` TEXT NOT NULL,
                `$COLUMN_MIME_TYPE` TEXT,
                `$COLUMN_FILENAME` TEXT,
                PRIMARY KEY(`$COLUMN_ID`)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            database.execSQL(
                "ALTER TABLE `$TABLE_ATTACHMENTS` ADD COLUMN `$COLUMN_MIME_TYPE` TEXT",
            )
        }
        if (oldVersion < 3) {
            database.execSQL(
                "ALTER TABLE `$TABLE_ATTACHMENTS` ADD COLUMN `$COLUMN_FILENAME` TEXT",
            )
        }
    }
}
