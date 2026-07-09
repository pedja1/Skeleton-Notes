package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.data.mapper.jsonToNextcloudNote
import org.skynetsoftware.skeletonnotes.data.mapper.nextcloudNoteToJson
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

        val json = nextcloudNoteToJson(note)
        val parsed = jsonToNextcloudNote(json)

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

        val json = nextcloudNoteToJson(note)
        val parsed = jsonToNextcloudNote(json)

        assertTrue(parsed.title == null || parsed.title == "")
        assertTrue(parsed.id == "abc")
    }

    @Test
    fun nextcloudNoteWithMissingStatus() {
        val json = """{"id":"abc","title":"T","content":"c","createdAt":0,"modifiedAt":0,"tags":[],"attachments":[]}"""
        val parsed = jsonToNextcloudNote(json)

        assertTrue(parsed.id == "abc")
        assertTrue(parsed.status == "" || parsed.status == "ACTIVE")
    }
}
