package org.skynetsoftware.skeletonnotes.data.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

internal class SkeletonNotesDatabaseHelper(
    application: Application
) : SQLiteOpenHelper(application, "skeleton-notes", null, 2) {

    companion object {
        //notes table
        const val TABLE_NOTES = "notes"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CREATED = "created"
        const val COLUMN_MODIFIED = "modified"
        const val COLUMN_TAGS = "tags"
        const val COLUMN_STATUS = "status"

        // table attachments
        const val TABLE_ATTACHMENTS = "attachments"
        const val COLUMN_NOTE_ID = "noteId"
        const val COLUMN_URI = "uri"
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE_NOTES` (
                `$COLUMN_ID` INTEGER NOT NULL,
                `$COLUMN_TITLE` TEXT,
                `$COLUMN_CONTENT` TEXT NOT NULL,
                `$COLUMN_CREATED` INTEGER NOT NULL,
                `$COLUMN_MODIFIED` INTEGER NOT NULL,
                `$COLUMN_TAGS` TEXT,
                `$COLUMN_STATUS` INTEGER NOT NULL DEFAULT ${NoteStatus.ACTIVE.value},
                PRIMARY KEY(`$COLUMN_ID`)
            )
        """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `$TABLE_ATTACHMENTS` (
                `$COLUMN_ID` INTEGER NOT NULL,
                `$COLUMN_NOTE_ID` INTEGER NOT NULL,
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
    }

}
