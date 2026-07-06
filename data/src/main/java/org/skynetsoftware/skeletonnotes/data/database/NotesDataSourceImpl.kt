package org.skynetsoftware.skeletonnotes.data.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Concrete [NotesDataSource] implementation backed by [SkeletonNotesDatabaseHelper].
 */
internal class NotesDataSourceImpl(
    private val skeletonNotesDatabaseHelper: SkeletonNotesDatabaseHelper
) : NotesDataSource {

    companion object {
        private const val TAG = "NotesDataSource"
    }

    override suspend fun getAllNotes(): Result<List<Note>> {
        var cursor: Cursor? = null
        return try {
            val database = skeletonNotesDatabaseHelper.writableDatabase
            cursor = database.rawQuery(
                "SELECT * FROM $TABLE_NOTES ORDER BY $COLUMN_MODIFIED DESC", null
            )
            val notes = cursor.toNotes()
            Log.d(TAG, "getAllNotes: ${notes.size}")
            Result.Success(notes)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            cursor?.close()
        }
    }

    override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> {
        var cursor: Cursor? = null
        return try {
            val database = skeletonNotesDatabaseHelper.writableDatabase
            cursor = database.rawQuery(
                """
                SELECT 
                    $TABLE_NOTES.*, 
                    $TABLE_ATTACHMENTS.$COLUMN_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_ID,
                    $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID,
                    $TABLE_ATTACHMENTS.$COLUMN_URI AS ${TABLE_ATTACHMENTS}_$COLUMN_URI
                FROM $TABLE_NOTES 
                LEFT JOIN $TABLE_ATTACHMENTS on $TABLE_NOTES.$COLUMN_ID = $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID
                WHERE $TABLE_NOTES.$COLUMN_ID = ?
                ORDER BY $TABLE_NOTES.$COLUMN_MODIFIED DESC
                """.trimIndent(),
                arrayOf(id.toString())
            )
            val note = cursor.toNoteWithAttachments()
            Log.d(TAG, "getNoteById: $id")
            Result.Success(note)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            cursor?.close()
        }
    }

    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> {
        var database: SQLiteDatabase? = null
        return try {
            database = skeletonNotesDatabaseHelper.writableDatabase
            database.beginTransaction()
            val id = database.insertWithOnConflict(
                TABLE_NOTES,
                null,
                noteWithAttachments.note.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            noteWithAttachments.attachments.forEach { attachment ->
                database.insertWithOnConflict(
                    TABLE_ATTACHMENTS,
                    null,
                    attachment.copy(noteId = id).toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            database.setTransactionSuccessful()
            Log.d(TAG, "saveNote: id=$id")
            Result.Success(id)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            database?.endTransaction()
        }
    }

    override suspend fun deleteNote(id: Long): Result<Unit> {
        var database: SQLiteDatabase? = null
        return try {
            database = skeletonNotesDatabaseHelper.writableDatabase
            database.beginTransaction()
            database.delete(TABLE_NOTES, "$COLUMN_ID = ?", arrayOf(id.toString()))
            database.delete(TABLE_ATTACHMENTS, "$COLUMN_NOTE_ID = ?", arrayOf(id.toString()))
            database.setTransactionSuccessful()
            Log.d(TAG, "deleteNote: $id")
            Result.Success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            database?.endTransaction()
        }
    }
}
