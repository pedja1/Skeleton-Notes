package org.skynetsoftware.skeletonnotes.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CONTENT
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_CREATED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_MODIFIED
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_NOTE_ID
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TAGS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_TITLE
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.COLUMN_URI
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_ATTACHMENTS
import org.skynetsoftware.skeletonnotes.data.database.SkeletonNotesDatabaseHelper.Companion.TABLE_NOTES
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note

@RunWith(AndroidJUnit4::class)
class NoteMapperInstrumentedTest {

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
    fun cursorToNoteConvertsCorrectly() {
        val cv = Note(
            id = 1,
            title = "Test Title",
            content = "# Content",
            createdAt = 1000L,
            modifiedAt = 2000L,
            tags = setOf("tag1", "tag2")
        ).toContentValues()
        database.insert(TABLE_NOTES, null, cv)

        val cursor = database.rawQuery("SELECT * FROM $TABLE_NOTES WHERE $COLUMN_ID = 1", null)
        assertTrue(cursor.moveToFirst())

        val note = cursor.toNote()
        assertEquals(1L, note.id)
        assertEquals("Test Title", note.title)
        assertEquals("# Content", note.content)
        assertEquals(1000L, note.createdAt)
        assertEquals(2000L, note.modifiedAt)
        assertEquals(setOf("tag1", "tag2"), note.tags)

        cursor.close()
    }

    @Test
    fun cursorToNoteHandlesNullTitle() {
        val cv = Note(
            id = 1,
            title = null,
            content = "Content",
            createdAt = 1000L,
            modifiedAt = 1000L,
            tags = emptySet()
        ).toContentValues()
        database.insert(TABLE_NOTES, null, cv)

        val cursor = database.rawQuery("SELECT * FROM $TABLE_NOTES WHERE $COLUMN_ID = 1", null)
        cursor.moveToFirst()

        val note = cursor.toNote()
        assertEquals(null, note.title)

        cursor.close()
    }

    @Test
    fun cursorToNotesConvertsMultipleRows() {
        database.insert(TABLE_NOTES, null, Note(1, "A", "a", 1000L, 1000L, emptySet()).toContentValues())
        database.insert(TABLE_NOTES, null, Note(2, "B", "b", 2000L, 2000L, emptySet()).toContentValues())

        val cursor = database.rawQuery("SELECT * FROM $TABLE_NOTES ORDER BY $COLUMN_ID", null)

        val notes = cursor.toNotes()
        assertEquals(2, notes.size)
        assertEquals("A", notes[0].title)
        assertEquals("B", notes[1].title)

        cursor.close()
    }

    @Test
    fun cursorToNotesReturnsEmptyListForNoRows() {
        val cursor = database.rawQuery("SELECT * FROM $TABLE_NOTES WHERE $COLUMN_ID = -1", null)

        val notes = cursor.toNotes()
        assertTrue(notes.isEmpty())

        cursor.close()
    }

    @Test
    fun cursorToNoteWithAttachmentsConvertsNoteAndAttachments() {
        database.insert(TABLE_NOTES, null, Note(1, "Note", "content", 1000L, 1000L, emptySet()).toContentValues())
        database.insert(TABLE_ATTACHMENTS, null, Attachment(1, 1, "file://photo.jpg").toContentValues())
        database.insert(TABLE_ATTACHMENTS, null, Attachment(2, 1, "file://audio.mp3").toContentValues())

        val cursor = database.rawQuery(
            """
            SELECT 
                $TABLE_NOTES.*, 
            $TABLE_ATTACHMENTS.$COLUMN_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_ID,
            $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID,
            $TABLE_ATTACHMENTS.$COLUMN_URI AS ${TABLE_ATTACHMENTS}_$COLUMN_URI
        FROM $TABLE_NOTES 
        LEFT JOIN $TABLE_ATTACHMENTS on $TABLE_NOTES.$COLUMN_ID = $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID
        WHERE $TABLE_NOTES.$COLUMN_ID = 1
            """.trimIndent(),
            null
        )

        val noteWithAttachments = cursor.toNoteWithAttachments()
        assertEquals("Note", noteWithAttachments.note.title)
        assertEquals(2, noteWithAttachments.attachments.size)
        assertEquals("file://photo.jpg", noteWithAttachments.attachments[0].uri)
        assertEquals("file://audio.mp3", noteWithAttachments.attachments[1].uri)

        cursor.close()
    }

    @Test
    fun cursorToNoteWithAttachmentsReturnsEmptyAttachmentsWhenNone() {
        database.insert(TABLE_NOTES, null, Note(1, "Note", "content", 1000L, 1000L, emptySet()).toContentValues())

        val cursor = database.rawQuery(
            """
            SELECT 
                $TABLE_NOTES.*, 
            $TABLE_ATTACHMENTS.$COLUMN_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_ID,
            $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID AS ${TABLE_ATTACHMENTS}_$COLUMN_NOTE_ID,
            $TABLE_ATTACHMENTS.$COLUMN_URI AS ${TABLE_ATTACHMENTS}_$COLUMN_URI
        FROM $TABLE_NOTES 
        LEFT JOIN $TABLE_ATTACHMENTS on $TABLE_NOTES.$COLUMN_ID = $TABLE_ATTACHMENTS.$COLUMN_NOTE_ID
        WHERE $TABLE_NOTES.$COLUMN_ID = 1
            """.trimIndent(),
            null
        )

        val noteWithAttachments = cursor.toNoteWithAttachments()
        assertEquals("Note", noteWithAttachments.note.title)
        assertTrue(noteWithAttachments.attachments.isEmpty())

        cursor.close()
    }

    @Test
    fun noteToContentValuesContainsAllFields() {
        val note = Note(
            id = 1,
            title = "My Title",
            content = "# Content",
            createdAt = 1000L,
            modifiedAt = 2000L,
            tags = setOf("kotlin", "android")
        )

        val cv = note.toContentValues()

        assertEquals(1L, cv.getAsLong(COLUMN_ID))
        assertEquals("My Title", cv.getAsString(COLUMN_TITLE))
        assertEquals("# Content", cv.getAsString(COLUMN_CONTENT))
        assertEquals(1000L, cv.getAsLong(COLUMN_CREATED))
        assertEquals(2000L, cv.getAsLong(COLUMN_MODIFIED))
        assertEquals("kotlin,android", cv.getAsString(COLUMN_TAGS))
    }

    @Test
    fun noteToContentValuesExcludesIdWhenZero() {
        val note = Note(
            id = 0,
            title = "New",
            content = "Content",
            createdAt = 1000L,
            modifiedAt = 1000L,
            tags = emptySet()
        )

        val cv = note.toContentValues()

        assertNull(cv.get(COLUMN_ID))
    }

    @Test
    fun noteToContentValuesHandlesNullTitle() {
        val note = Note(
            id = 1,
            title = null,
            content = "Content",
            createdAt = 1000L,
            modifiedAt = 1000L,
            tags = emptySet()
        )

        val cv = note.toContentValues()

        assertNull(cv.get(COLUMN_TITLE))
    }

    @Test
    fun noteToContentValuesHandlesEmptyTags() {
        val note = Note(
            id = 1,
            title = "Title",
            content = "Content",
            createdAt = 1000L,
            modifiedAt = 1000L,
            tags = emptySet()
        )

        val cv = note.toContentValues()

        assertEquals("", cv.getAsString(COLUMN_TAGS))
    }

    @Test
    fun attachmentToContentValuesContainsAllFields() {
        val attachment = Attachment(id = 1, noteId = 42, uri = "file://test/photo.jpg")

        val cv = attachment.toContentValues()

        assertEquals(1L, cv.getAsLong(COLUMN_ID))
        assertEquals(42L, cv.getAsLong(COLUMN_NOTE_ID))
        assertEquals("file://test/photo.jpg", cv.getAsString(COLUMN_URI))
    }

    @Test
    fun attachmentToContentValuesExcludesIdWhenZero() {
        val attachment = Attachment(id = 0, noteId = 42, uri = "file://test/photo.jpg")

        val cv = attachment.toContentValues()

        assertNull(cv.get(COLUMN_ID))
    }
}
