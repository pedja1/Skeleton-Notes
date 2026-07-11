package org.skynetsoftware.skeletonnotes.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

/** Serializes notes to the JSON array stored as `notes.json` in the backup archive. */
internal fun List<NoteWithAttachments>.toJsonString(): String {
    val array = JSONArray()
    forEach { noteWithAttachments ->
        val note = noteWithAttachments.note
        array.put(
            JSONObject().apply {
                put("id", note.id)
                put("title", note.title ?: "")
                put("content", note.content)
                put("createdAt", note.createdAt)
                put("modifiedAt", note.modifiedAt)
                put("tags", JSONArray(note.tags.toList()))
                put("status", note.status.value)
                put(
                    "attachments",
                    JSONArray().apply {
                        noteWithAttachments.attachments.forEach { att ->
                            put(
                                JSONObject().apply {
                                    put("id", att.id)
                                    att.mimeType?.let { put("mimeType", it) }
                                },
                            )
                        }
                    },
                )
            },
        )
    }
    return array.toString()
}

/** Parses the backup archive's `notes.json` back into notes with their attachment metadata. */
internal fun JSONArray.toNotes(): List<NoteWithAttachments> =
    buildList {
        for (i in 0 until length()) {
            val obj = getJSONObject(i)
            val noteId = obj.getString("id")
            val attachmentsArray = obj.optJSONArray("attachments") ?: JSONArray()
            val attachments =
                buildList {
                    for (j in 0 until attachmentsArray.length()) {
                        val att = attachmentsArray.getJSONObject(j)
                        add(
                            Attachment(
                                id = att.getString("id"),
                                noteId = noteId,
                                uri = "",
                                mimeType = att.optString("mimeType").takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                }
            val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
            val tags =
                buildSet {
                    for (j in 0 until tagsArray.length()) {
                        add(tagsArray.getString(j))
                    }
                }
            add(
                NoteWithAttachments(
                    note =
                        Note(
                            id = noteId,
                            title = obj.optString("title").takeIf { it.isNotEmpty() },
                            content = obj.getString("content"),
                            createdAt = obj.getLong("createdAt"),
                            modifiedAt = obj.getLong("modifiedAt"),
                            tags = tags,
                            status = NoteStatus.fromValue(obj.optInt("status", NoteStatus.ACTIVE.value)),
                        ),
                    attachments = attachments,
                ),
            )
        }
    }
