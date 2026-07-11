package org.skynetsoftware.skeletonnotes.data.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_FILENAME
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MIME_TYPE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_STATUS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES
import org.skynetsoftware.skeletonnotes.data.mapper.toContentValues
import org.skynetsoftware.skeletonnotes.data.mapper.toNoteWithAttachments
import org.skynetsoftware.skeletonnotes.data.mapper.toNotes
import org.skynetsoftware.skeletonnotes.data.mapper.toNotesWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete [NotesDataSource] implementation backed by [SkeletonNotesDatabaseHelper].
 */
internal class NotesDataSourceImpl(
    private val skeletonNotesDatabaseHelper: SkeletonNotesDatabaseHelper,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotesDataSource {
    companion object {
        private const val TAG = "NotesDataSource"
    }

    private val getNoteByIdFlows = ConcurrentHashMap<String, MutableSharedFlow<Unit>>()
    private val getAllNotesChangedFlow = MutableSharedFlow<Unit>(replay = 1)

    init {
        getAllNotesChangedFlow.tryEmit(Unit)
    }

    /**
     * @see NotesDataSource.getAllNotesFlow
     * @see NotesDataSource.getAllNotes
     * @see NotesDataSourceImpl.getAllNotes
     * Flow will emit new list when change is detected
     */
    override fun getAllNotesFlow(): Flow<Result<List<Note>>> =
        getAllNotesChangedFlow.map {
            getAllNotes()
        }

    /**
     * @see NotesDataSource.getAllNotes
     */
    override suspend fun getAllNotes(): Result<List<Note>> =
        withContext(coroutineDispatcher) {
            var cursor: Cursor? = null
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                cursor =
                    database.rawQuery(
                        "SELECT * FROM $TABLE_NOTES ORDER BY $COLUMN_MODIFIED DESC",
                        null,
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

    /**
     * @see NotesDataSource.getAllNotesWithAttachmentsFlow
     */
    override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
        getAllNotesChangedFlow.map {
            getAllNotesWithAttachments()
        }

    override suspend fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> =
        withContext(coroutineDispatcher) {
            var cursor: Cursor? = null
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                cursor =
                    database.rawQuery(
                        """
                        SELECT
                            $TABLE_NOTES.*,
                            $TABLE_ATTACHMENTS.$COLUMN_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_ID,
                            $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID,
                            $TABLE_ATTACHMENTS.$COLUMN_URI AS ${TABLE_ATTACHMENTS}_$COLUMN_URI,
                            $TABLE_ATTACHMENTS.$COLUMN_MIME_TYPE AS ${TABLE_ATTACHMENTS}_$COLUMN_MIME_TYPE,
                            $TABLE_ATTACHMENTS.$COLUMN_FILENAME AS ${TABLE_ATTACHMENTS}_$COLUMN_FILENAME
                        FROM $TABLE_NOTES
                        LEFT JOIN $TABLE_ATTACHMENTS on $TABLE_NOTES.$COLUMN_ID = $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID
                        ORDER BY $TABLE_NOTES.$COLUMN_MODIFIED DESC, $TABLE_NOTES.$COLUMN_ID
                        """.trimIndent(),
                        null,
                    )
                val notes = cursor.toNotesWithAttachments()
                Log.d(TAG, "getAllNotesWithAttachments: ${notes.size}")
                Result.Success(notes)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            } finally {
                cursor?.close()
            }
        }

    /**
     * @see NotesDataSource.getNoteByIdFlow
     */
    override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> {
        val flow =
            getNoteByIdFlows.getOrPut(id) {
                MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
            }
        return flow.map {
            getNoteById(id)
        }
    }

    /**
     * @see NotesDataSource.getNoteById
     */
    override suspend fun getNoteById(id: String): Result<NoteWithAttachments> =
        withContext(coroutineDispatcher) {
            var cursor: Cursor? = null
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                cursor =
                    database.rawQuery(
                        """
                        SELECT
                            $TABLE_NOTES.*,
                            $TABLE_ATTACHMENTS.$COLUMN_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_ID,
                            $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID,
                            $TABLE_ATTACHMENTS.$COLUMN_URI AS ${TABLE_ATTACHMENTS}_$COLUMN_URI,
                            $TABLE_ATTACHMENTS.$COLUMN_MIME_TYPE AS ${TABLE_ATTACHMENTS}_$COLUMN_MIME_TYPE,
                            $TABLE_ATTACHMENTS.$COLUMN_FILENAME AS ${TABLE_ATTACHMENTS}_$COLUMN_FILENAME
                        FROM $TABLE_NOTES
                        LEFT JOIN $TABLE_ATTACHMENTS on $TABLE_NOTES.$COLUMN_ID = $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID
                        WHERE $TABLE_NOTES.$COLUMN_ID = ?
                        ORDER BY $TABLE_NOTES.$COLUMN_MODIFIED DESC
                        """.trimIndent(),
                        arrayOf(id),
                    )
                if (!cursor.moveToFirst()) {
                    return@withContext Result.Failure(IllegalStateException("Note not found: $id"))
                }
                cursor.moveToPosition(-1)
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
     * @see NotesDataSource.saveNote
     */
    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> =
        withContext(coroutineDispatcher) {
            var database: SQLiteDatabase? = null
            var committed = false
            try {
                database = skeletonNotesDatabaseHelper.writableDatabase
                database.beginTransaction()
                insertNoteWithAttachments(database, noteWithAttachments)
                database.setTransactionSuccessful()
                committed = true
                Log.d(TAG, "saveNote: id=${noteWithAttachments.note.id}")
                Result.Success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            } finally {
                database?.endTransaction()
                if (committed) {
                    notifyNotesChanged(noteWithAttachments.note.id)
                }
            }
        }

    override suspend fun saveNotes(notesWithAttachments: List<NoteWithAttachments>): Result<Unit> =
        withContext(coroutineDispatcher) {
            var database: SQLiteDatabase? = null
            var committed = false
            try {
                database = skeletonNotesDatabaseHelper.writableDatabase
                database.beginTransaction()
                notesWithAttachments.forEach { noteWithAttachments ->
                    insertNoteWithAttachments(database, noteWithAttachments)
                }
                database.setTransactionSuccessful()
                committed = true
                Log.d(TAG, "saveNotes: count=${notesWithAttachments.size}")
                Result.Success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            } finally {
                database?.endTransaction()
                if (committed) {
                    // Single notification after commit so observers re-query once for the whole batch.
                    notifyNotesChanged(null)
                }
            }
        }

    /**
     * Upserts [noteWithAttachments] and reconciles its attachments. Runs no transaction and sends no
     * change notification; callers own the enclosing transaction and observer notification.
     */
    private fun insertNoteWithAttachments(
        database: SQLiteDatabase,
        noteWithAttachments: NoteWithAttachments,
    ) {
        database.insertWithOnConflict(
            TABLE_NOTES,
            null,
            noteWithAttachments.note.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        val newAttachmentIds = noteWithAttachments.attachments.map { it.id }.toSet()
        val existingAttachmentIds = mutableSetOf<String>()
        database
            .rawQuery(
                "SELECT $COLUMN_ID FROM $TABLE_ATTACHMENTS WHERE $COLUMN_NOTE_ID = ?",
                arrayOf(noteWithAttachments.note.id),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingAttachmentIds.add(cursor.getString(0))
                }
            }
        for (existingId in existingAttachmentIds) {
            if (existingId !in newAttachmentIds) {
                database.delete(TABLE_ATTACHMENTS, "$COLUMN_ID = ?", arrayOf(existingId))
            }
        }
        noteWithAttachments.attachments.forEach { attachment ->
            database.insertWithOnConflict(
                TABLE_ATTACHMENTS,
                null,
                attachment.copy(noteId = noteWithAttachments.note.id).toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    /**
     * @see NotesDataSource.deleteNote
     */
    override suspend fun deleteNote(id: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            var database: SQLiteDatabase? = null
            try {
                database = skeletonNotesDatabaseHelper.writableDatabase
                database.beginTransaction()
                database.delete(TABLE_NOTES, "$COLUMN_ID = ?", arrayOf(id))
                database.delete(TABLE_ATTACHMENTS, "$COLUMN_NOTE_ID = ?", arrayOf(id))
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

    /**
     * @see NotesDataSource.moveToTrash
     */
    override suspend fun moveToTrash(id: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                val values =
                    ContentValues().apply {
                        put(COLUMN_STATUS, NoteStatus.TRASH.value)
                    }
                database.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id))
                Log.d(TAG, "moveToTrash: $id")
                notifyNotesChanged(id)
                Result.Success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            }
        }

    /**
     * @see NotesDataSource.archiveNote
     */
    override suspend fun archiveNote(id: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                val values =
                    ContentValues().apply {
                        put(COLUMN_STATUS, NoteStatus.ARCHIVE.value)
                    }
                database.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id))
                Log.d(TAG, "archiveNote: $id")
                notifyNotesChanged(id)
                Result.Success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            }
        }

    /**
     * @see NotesDataSource.restoreNote
     */
    override suspend fun restoreNote(id: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val database = skeletonNotesDatabaseHelper.writableDatabase
                val values =
                    ContentValues().apply {
                        put(COLUMN_STATUS, NoteStatus.ACTIVE.value)
                    }
                database.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(id))
                Log.d(TAG, "restoreNote: $id")
                notifyNotesChanged(id)
                Result.Success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, null, t)
                Result.Failure(t)
            }
        }

    /**
     * trigger re-read of all notes or note by id, for all functions that return Flow
     */
    private suspend fun notifyNotesChanged(noteId: String?) {
        getAllNotesChangedFlow.emit(Unit)
        if (noteId != null) {
            getNoteByIdFlows[noteId]?.emit(Unit)
        }
    }
}
