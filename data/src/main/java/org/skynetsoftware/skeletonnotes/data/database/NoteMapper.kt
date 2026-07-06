package org.skynetsoftware.skeletonnotes.data.database

import android.content.ContentValues
import android.database.Cursor
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CONTENT
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CREATED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TAGS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TITLE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

fun Cursor.toNote(): Note {
    return Note(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        title = getStringOrNull(getColumnIndexOrThrow(COLUMN_TITLE)),
        content = getString(getColumnIndexOrThrow(COLUMN_CONTENT)),
        createdAt = getLong(getColumnIndexOrThrow(COLUMN_CREATED)),
        modifiedAt = getLong(getColumnIndexOrThrow(COLUMN_MODIFIED)),
        tags = getStringOrNull(getColumnIndexOrThrow(COLUMN_TAGS))?.splitToSequence(",").orEmpty().toSet(),
    )
}

fun Cursor.toNoteWithAttachments(): NoteWithAttachments {
    var note: Note? = null
    val attachments = ArrayList<Attachment>()
    val attachmentIdColumnIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_ID")
    val attachmentNoteIdColumnIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID")
    val attachmentUriColumnIndex = getColumnIndexOrThrow("${TABLE_ATTACHMENTS}_$COLUMN_URI")
    while (moveToNext()) {
        if(note == null) {
            note = toNote()
        }
        if (!isNull(attachmentIdColumnIndex)) {
            attachments.add(Attachment(
                id = getLong(attachmentIdColumnIndex),
                noteId = getLong(attachmentNoteIdColumnIndex),
                uri = getString(attachmentUriColumnIndex),
            ))
        }
    }
    return NoteWithAttachments(note ?: error("Failed to parse note from cursor"), attachments)
}

fun Cursor.toNotes(): List<Note> {
    return buildList {
        while (moveToNext()) {
            add(toNote())
        }
    }
}

fun Note.toContentValues(): ContentValues {
    return ContentValues().apply {
        if(id > 0 ) {
            put(COLUMN_ID, id)
        }
        put(COLUMN_TITLE, title)
        put(COLUMN_CONTENT, content)
        put(COLUMN_CREATED, createdAt)
        put(COLUMN_MODIFIED, modifiedAt)
        put(COLUMN_TAGS, tags.joinToString(","))
    }
}

fun Attachment.toContentValues(): ContentValues {
    return ContentValues().apply {
        if(id > 0) {
            put(COLUMN_ID, id)
        }
        put(COLUMN_NOTE_ID, noteId)
        put(COLUMN_URI, uri)
    }
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if(isNull(index)) null else getString(index)