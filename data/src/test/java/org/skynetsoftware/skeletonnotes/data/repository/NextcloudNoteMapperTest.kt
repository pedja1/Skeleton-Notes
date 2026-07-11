package org.skynetsoftware.skeletonnotes.data.repository

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.data.mapper.toJson
import org.skynetsoftware.skeletonnotes.data.mapper.toNextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudAttachment
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote

class NextcloudNoteMapperTest {

    @Test
    fun nextcloudNoteToJsonAndBack() {
        val note = NextcloudNote(
            id = "abc123",
            title = "Test Note",
            content = "<h1>Hello</h1>",
            createdAt = 1000L,
            modifiedAt = 2000L,
            tags = setOf("tag1", "tag2"),
            status = "ACTIVE",
            attachments = listOf(
                NextcloudAttachment(id = "att1", filename = "image.jpg"),
                NextcloudAttachment(id = "att2", filename = "doc.pdf"),
            ),
        )

        val json = note.toJson()
        val parsed = JSONObject(json).toNextcloudNote()

        assertTrue(json.contains("abc123"))
        assertTrue(json.contains("Test Note"))
        assertTrue(json.contains("tag1"))
        assertTrue(json.contains("image.jpg"))
        assertTrue(parsed.id == note.id)
        assertTrue(parsed.title == note.title)
        assertTrue(parsed.content == note.content)
        assertTrue(parsed.tags == note.tags)
        assertTrue(parsed.status == note.status)
        assertTrue(parsed.attachments.size == 2)
    }

    @Test
    fun nextcloudNoteWithEmptyTitle() {
        val note = NextcloudNote(
            id = "abc",
            title = null,
            content = "content",
            createdAt = 0L,
            modifiedAt = 0L,
            tags = emptySet(),
            status = "ACTIVE",
            attachments = emptyList(),
        )

        val json = note.toJson()
        val parsed = JSONObject(json).toNextcloudNote()

        assertTrue(parsed.title == null || parsed.title == "")
        assertTrue(parsed.id == "abc")
    }

    @Test
    fun nextcloudNoteWithMissingStatus() {
        val json = """{"id":"abc","title":"T","content":"c","createdAt":0,"modifiedAt":0,"tags":[],"attachments":[]}"""
        val parsed = JSONObject(json).toNextcloudNote()

        assertTrue(parsed.id == "abc")
        assertTrue(parsed.status == "" || parsed.status == "ACTIVE")
    }
}
