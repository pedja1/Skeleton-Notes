package org.skynetsoftware.skeletonnotes.data.attachment

import android.content.Context
import android.net.Uri
import org.skynetsoftware.skeletonnotes.domain.repository.AttachmentFileStorage
import java.io.File

/**
 * Manages local storage of attachment files. Copies content:// URIs
 * to internal storage so files are available for sync and across reboots.
 */
class AttachmentStorageManager(private val context: Context) : AttachmentFileStorage {

    private val attachmentsDir: File
        get() {
            val dir = File(context.filesDir, "attachments")
            dir.mkdirs()
            return dir
        }

    /**
     * Copies a file from a URI to internal storage.
     *
     * @param sourceUri the source URI (content://, file://)
     * @param attachmentId the attachment ID to use as filename
     * @param filename the original filename for reference
     * @return the local file absolute path
     */
    fun copyToStorage(sourceUri: Uri, attachmentId: String, filename: String): String {
        val file = File(attachmentsDir, attachmentId)

        val inputStream = when {
            sourceUri.scheme == "file" -> File(sourceUri.path!!).inputStream()
            else -> context.contentResolver.openInputStream(sourceUri)
                ?: error("Cannot open content URI: $sourceUri")
        }

        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file.absolutePath
    }

    /**
     * Returns the local file for the given [attachmentId].
     */
    override fun getFile(attachmentId: String): File {
        return File(attachmentsDir, attachmentId)
    }

    /**
     * Writes raw bytes to internal storage for the given [attachmentId].
     *
     * @param attachmentId the attachment ID to use as filename
     * @param bytes the file content to write
     * @return the local file absolute path
     */
    override fun writeBytes(attachmentId: String, bytes: ByteArray): String {
        val file = File(attachmentsDir, attachmentId)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Deletes the local file for the given [attachmentId].
     */
    override fun deleteFile(attachmentId: String) {
        File(attachmentsDir, attachmentId).delete()
    }
}
