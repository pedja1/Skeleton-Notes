package org.skynetsoftware.skeletonnotes.data.mapper

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

class BackupNoteMapperTest {
    @Test
    fun `round trips all note fields`() {
        val original =
            listOf(
                NoteWithAttachments(
                    note =
                        Note(
                            id = "note-1",
                            title = "Title",
                            content = "# Markdown body",
                            createdAt = 100L,
                            modifiedAt = 200L,
                            tags = setOf("work", "personal"),
                            status = NoteStatus.ARCHIVE,
                            remoteLastModified = 999L,
                        ),
                    attachments =
                        listOf(
                            Attachment(id = "att-1", noteId = "note-1", uri = "/tmp/att-1", mimeType = "image/jpeg"),
                        ),
                ),
            )

        val restored = JSONArray(original.toJsonString()).toNotes()

        assertEquals(1, restored.size)
        val note = restored.single().note
        assertEquals("note-1", note.id)
        assertEquals("Title", note.title)
        assertEquals("# Markdown body", note.content)
        assertEquals(100L, note.createdAt)
        assertEquals(200L, note.modifiedAt)
        assertEquals(setOf("work", "personal"), note.tags)
        assertEquals(NoteStatus.ARCHIVE, note.status)
        // remoteLastModified is sync-internal and intentionally not serialized.
        assertEquals(0L, note.remoteLastModified)

        val attachment = restored.single().attachments.single()
        assertEquals("att-1", attachment.id)
        assertEquals("note-1", attachment.noteId)
        assertEquals("image/jpeg", attachment.mimeType)
        // uri is a device-local path and is not serialized.
        assertEquals("", attachment.uri)
    }

    @Test
    fun attachmentFilenameRoundTrips() {
        val original =
            listOf(
                NoteWithAttachments(
                    note = Note("note-1", null, "body", 100L, 200L, emptySet()),
                    attachments =
                        listOf(
                            Attachment(
                                id = "att-1",
                                noteId = "note-1",
                                uri = "/tmp/att-1",
                                mimeType = "application/pdf",
                                filename = "report.pdf",
                            ),
                        ),
                ),
            )

        val restored = JSONArray(original.toJsonString()).toNotes()

        // Losing the filename on a backup round-trip would degrade the display name locally
        // and the upload name on the next Nextcloud push.
        assertEquals(
            "report.pdf",
            restored
                .single()
                .attachments
                .single()
                .filename,
        )
    }

    @Test
    fun attachmentWithoutFilenameRoundTripsAsNull() {
        val original =
            listOf(
                NoteWithAttachments(
                    note = Note("note-1", null, "body", 100L, 200L, emptySet()),
                    attachments = listOf(Attachment(id = "att-1", noteId = "note-1", uri = "/tmp/att-1")),
                ),
            )

        val restored = JSONArray(original.toJsonString()).toNotes()

        assertNull(
            restored
                .single()
                .attachments
                .single()
                .filename,
        )
    }

    @Test
    fun `blank title serializes and deserializes as null`() {
        val notes =
            listOf(
                NoteWithAttachments(
                    note = Note("id", null, "content", 0L, 0L, emptySet()),
                    attachments = emptyList(),
                ),
            )

        val restored = JSONArray(notes.toJsonString()).toNotes()

        assertNull(restored.single().note.title)
    }

    @Test
    fun `attachment without mime type round trips as null`() {
        val notes =
            listOf(
                NoteWithAttachments(
                    note = Note("id", "t", "c", 0L, 0L, emptySet()),
                    attachments = listOf(Attachment(id = "a", noteId = "id", uri = "u", mimeType = null)),
                ),
            )

        val restored = JSONArray(notes.toJsonString()).toNotes()

        assertNull(
            restored
                .single()
                .attachments
                .single()
                .mimeType,
        )
    }

    @Test
    fun `unknown status value falls back to active`() {
        val json = """[{"id":"id","title":"","content":"c","createdAt":0,"modifiedAt":0,"tags":[],"status":42}]"""

        val restored = JSONArray(json).toNotes()

        assertEquals(NoteStatus.ACTIVE, restored.single().note.status)
    }

    @Test
    fun `missing optional fields default gracefully`() {
        val json = """[{"id":"id","content":"c","createdAt":0,"modifiedAt":0}]"""

        val restored = JSONArray(json).toNotes()

        val note = restored.single().note
        assertNull(note.title)
        assertTrue(note.tags.isEmpty())
        assertTrue(restored.single().attachments.isEmpty())
        assertEquals(NoteStatus.ACTIVE, note.status)
    }

    @Test
    fun `empty list produces empty json array`() {
        val restored = JSONArray(emptyList<NoteWithAttachments>().toJsonString()).toNotes()

        assertTrue(restored.isEmpty())
    }
}
