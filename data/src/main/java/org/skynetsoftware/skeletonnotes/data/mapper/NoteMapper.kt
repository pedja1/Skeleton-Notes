package org.skynetsoftware.skeletonnotes.data.mapper

import android.content.ContentValues
import android.database.Cursor
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CONTENT
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CREATED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MIME_TYPE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_REMOTE_LAST_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_STATUS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TAGS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TITLE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

/**
 * Converts this [Cursor] to a [Note] entity.
 */
internal fun Cursor.toNote(): Note =
    Note(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        title = getStringOrNull(getColumnIndexOrThrow(COLUMN_TITLE)),
        content = getString(getColumnIndexOrThrow(COLUMN_CONTENT)),
        createdAt = getLong(getColumnIndexOrThrow(COLUMN_CREATED)),
        modifiedAt = getLong(getColumnIndexOrThrow(COLUMN_MODIFIED)),
        tags =
            getStringOrNull(getColumnIndexOrThrow(COLUMN_TAGS))
                ?.splitToSequence(",")
                .orEmpty()
                .filter { it.isNotBlank() }
                .toSet(),
        status = NoteStatus.fromValue(getInt(getColumnIndexOrThrow(COLUMN_STATUS))),
        remoteLastModified = getLong(getColumnIndexOrThrow(COLUMN_REMOTE_LAST_MODIFIED)),
    )

/**
 * Converts this [Cursor] (result of a note+attachments join query) to a [NoteWithAttachments].
 * The cursor must be positioned before the first row.
 */
internal fun Cursor.toNoteWithAttachments(): NoteWithAttachments {
    var note: Note? = null
    val attachments = ArrayList<Attachment>()
    while (moveToNext()) {
        if (note == null) {
            note = toNote()
        }
        toAttachmentFromJoin()?.let { attachments.add(it) }
    }
    return NoteWithAttachments(note ?: error("Failed to parse note from cursor"), attachments)
}

/**
 * Converts this [Cursor] (result of a notes+attachments join query, ordered so that the rows
 * for a note are contiguous) to a list of [NoteWithAttachments], grouping the joined attachment
 * rows under each note. The cursor must be positioned before the first row.
 */
internal fun Cursor.toNotesWithAttachments(): List<NoteWithAttachments> {
    val result = ArrayList<NoteWithAttachments>()
    var currentNote: Note? = null
    var currentAttachments = ArrayList<Attachment>()
    while (moveToNext()) {
        val note = toNote()
        if (currentNote == null || currentNote.id != note.id) {
            currentNote?.let { result.add(NoteWithAttachments(it, currentAttachments)) }
            currentNote = note
            currentAttachments = ArrayList()
        }
        toAttachmentFromJoin()?.let { currentAttachments.add(it) }
    }
    currentNote?.let { result.add(NoteWithAttachments(it, currentAttachments)) }
    return result
}

/**
 * Resolves and reads the aliased attachment columns from a notes+attachments join cursor.
 * Returns `null` for a row whose attachment side is NULL (a note with no attachments in a
 * LEFT JOIN).
 */
internal fun Cursor.toAttachmentFromJoin(): Attachment? {
    val idIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_ID")
    if (isNull(idIndex)) return null

    val noteIdIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID")
    val uriIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_URI")
    val mimeTypeIndex = getColumnIndex("${TABLE_ATTACHMENTS}_$COLUMN_MIME_TYPE")
    return Attachment(
        id = getString(idIndex),
        noteId = getString(noteIdIndex),
        uri = getString(uriIndex),
        mimeType = getStringOrNull(mimeTypeIndex),
    )
}

/**
 * Converts this [Cursor] to a list of [Note] entities.
 * The cursor must be positioned before the first row.
 */
internal fun Cursor.toNotes(): List<Note> =
    buildList {
        while (moveToNext()) {
            add(toNote())
        }
    }

/**
 * Converts this [Note] to a [ContentValues] map suitable for SQLite insert/update operations.
 */
internal fun Note.toContentValues(): ContentValues =
    ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_TITLE, title)
        put(COLUMN_CONTENT, content)
        put(COLUMN_CREATED, createdAt)
        put(COLUMN_MODIFIED, modifiedAt)
        put(COLUMN_TAGS, tags.joinToString(","))
        put(COLUMN_STATUS, status.value)
        put(COLUMN_REMOTE_LAST_MODIFIED, remoteLastModified)
    }

/**
 * Converts this [Attachment] to a [ContentValues] map suitable for SQLite insert/update operations.
 */
internal fun Attachment.toContentValues(): ContentValues =
    ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_NOTE_ID, noteId)
        put(COLUMN_URI, uri)
        put(COLUMN_MIME_TYPE, mimeType)
    }

/**
 * Returns the string value at the given column [index], or null if the column is SQL NULL.
 */
private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
