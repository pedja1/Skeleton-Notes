package org.skynetsoftware.skeletonnotes.data.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_STATUS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
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

    private val getNoteByIdFlows = hashMapOf<Long, MutableSharedFlow<Unit>>()
    private val getAllNotesChangedFlow = MutableSharedFlow<Unit>(replay = 1)

    init {
        getAllNotesChangedFlow.tryEmit(Unit)
    }

    /**
     * Retrieves all notes from the database ordered by modification time descending.
     */
    override fun getAllNotes(): Flow<Result<List<Note>>> {
        return getAllNotesChangedFlow.map {
            var cursor: Cursor? = null
            try {
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
    }

    /**
     * Retrieves a single note with all its attachments by its [id].
     */
    override fun getNoteByIdFlow(id: Long): Flow<Result<NoteWithAttachments>> {
        var flow = getNoteByIdFlows[id]
        if (flow == null) {
            getNoteByIdFlows[id] = MutableSharedFlow(replay = 1)
            flow = getNoteByIdFlows[id]
            flow?.tryEmit(Unit)
        }
        return flow!!.map {
            getNoteById(id)
        }
    }

    /**
     * Retrieves a single note with all its attachments by its [id].
     */
    override fun getNoteById(id: Long): Result<NoteWithAttachments> {
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

    /**
     * Saves a [noteWithAttachments] to the database, replacing any existing record with the same ID.
     * Returns the ID of the saved note.
     */
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
            notifyNotesChanged(id) //TODO should this be after endTransaction
            Log.d(TAG, "saveNote: id=$id")
            Result.Success(id)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            database?.endTransaction()
        }
    }

    /**
     * Deletes the note identified by [id] along with all its attachments.
     */
    override suspend fun deleteNote(id: Long): Result<Unit> {
        var database: SQLiteDatabase? = null
        return try {
            database = skeletonNotesDatabaseHelper.writableDatabase
            database.beginTransaction()
            database.delete(TABLE_NOTES, "$COLUMN_ID = ?", arrayOf(id.toString()))
            database.delete(TABLE_ATTACHMENTS, "$COLUMN_NOTE_ID = ?", arrayOf(id.toString()))
            database.setTransactionSuccessful()
            Log.d(TAG, "deleteNote: $id")
            notifyNotesChanged(id)
            Result.Success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        } finally {
            database?.endTransaction()
        }
    }

    override suspend fun moveToTrash(id: Long): Result<Unit> {
        return try {
            val database = skeletonNotesDatabaseHelper.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_STATUS, NoteStatus.TRASH.value)
            }
            database.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
            Log.d(TAG, "moveToTrash: $id")
            notifyNotesChanged(id)
            Result.Success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        }
    }

    override suspend fun archiveNote(id: Long): Result<Unit> {
        return try {
            val database = skeletonNotesDatabaseHelper.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_STATUS, NoteStatus.ARCHIVE.value)
            }
            database.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
            Log.d(TAG, "archiveNote: $id")
            notifyNotesChanged(id)
            Result.Success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, null, t)
            Result.Failure(t)
        }
    }

    private suspend fun notifyNotesChanged(noteId: Long?) {
        getAllNotesChangedFlow.emit(Unit)
        if (noteId != null) {
            getNoteByIdFlows[noteId]?.emit(Unit)
        }
    }
}
