package org.skynetsoftware.skeletonnotes.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudAttachment
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote

/**
 * Serializes a [NextcloudNote] to its JSON representation for WebDAV storage.
 *
 * @param note the note to serialize
 * @return JSON string containing all note properties and attachments
 */
internal fun nextcloudNoteToJson(note: NextcloudNote): String {
    return JSONObject().apply {
        put("id", note.id)
        put("title", note.title ?: "")
        put("content", note.content)
        put("createdAt", note.createdAt)
        put("modifiedAt", note.modifiedAt)
        put("tags", JSONArray(note.tags.toList()))
        put("status", note.status)
        put("attachments", JSONArray().apply {
            note.attachments.forEach { att ->
                put(JSONObject().apply {
                    put("id", att.id)
                    put("filename", att.filename)
                })
            }
        })
    }.toString()
}

/**
 * Deserializes a JSON string from WebDAV storage into a [NextcloudNote].
 *
 * @param json the raw JSON string
 * @return parsed [NextcloudNote] with all properties and attachments
 */
internal fun jsonToNextcloudNote(json: String): NextcloudNote {
    val obj = JSONObject(json)
    val attachmentsArray = obj.optJSONArray("attachments") ?: JSONArray()
    val attachments = buildList {
        for (i in 0 until attachmentsArray.length()) {
            val att = attachmentsArray.getJSONObject(i)
            add(
                NextcloudAttachment(
                    id = att.getString("id"),
                    filename = att.getString("filename"),
                )
            )
        }
    }
    val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
    val tags = buildSet {
        for (i in 0 until tagsArray.length()) {
            add(tagsArray.getString(i))
        }
    }
    return NextcloudNote(
        id = obj.getString("id"),
        title = obj.optString("title").takeIf { it.isNotEmpty() },
        content = obj.getString("content"),
        createdAt = obj.getLong("createdAt"),
        modifiedAt = obj.getLong("modifiedAt"),
        tags = tags,
        status = obj.optString("status"),
        attachments = attachments,
    )
}
