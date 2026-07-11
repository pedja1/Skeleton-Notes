package org.skynetsoftware.skeletonnotes.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudAttachment
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote

/**
 * Serializes a [NextcloudNote] to its JSON representation for WebDAV storage.
 *
 * @return JSON string containing all note properties and attachments
 */
internal fun NextcloudNote.toJson(): String {
    return JSONObject().apply {
        put("id", id)
        put("title", title ?: "")
        put("content", content)
        put("createdAt", createdAt)
        put("modifiedAt", modifiedAt)
        put("tags", JSONArray(tags.toList()))
        put("status", status)
        put("attachments", JSONArray().apply {
            attachments.forEach { att ->
                put(JSONObject().apply {
                    put("id", att.id)
                    put("filename", att.filename)
                    att.mimeType?.let { put("mimeType", it) }
                })
            }
        })
    }.toString()
}

/**
 * Deserializes a JSON string from WebDAV storage into a [NextcloudNote].
 *
 * @return parsed [NextcloudNote] with all properties and attachments
 */
internal fun JSONObject.toNextcloudNote(): NextcloudNote {
    val attachmentsArray = optJSONArray("attachments") ?: JSONArray()
    val attachments = buildList {
        for (i in 0 until attachmentsArray.length()) {
            val att = attachmentsArray.getJSONObject(i)
            add(
                NextcloudAttachment(
                    id = att.getString("id"),
                    filename = att.getString("filename"),
                    mimeType = att.optString("mimeType").takeIf { it.isNotEmpty() },
                )
            )
        }
    }
    val tagsArray = optJSONArray("tags") ?: JSONArray()
    val tags = buildSet {
        for (i in 0 until tagsArray.length()) {
            add(tagsArray.getString(i))
        }
    }
    return NextcloudNote(
        id = getString("id"),
        title = optString("title").takeIf { it.isNotEmpty() },
        content = getString("content"),
        createdAt = getLong("createdAt"),
        modifiedAt = getLong("modifiedAt"),
        tags = tags,
        status = optString("status"),
        attachments = attachments,
    )
}
