package org.skynetsoftware.skeletonnotes.domain.attachment

import java.io.File
import java.io.InputStream

/**
 * Abstraction for local attachment file storage, allowing the sync use case
 * to read and write attachment bytes without depending on Android specifics.
 */
interface AttachmentFileStorage {
    /**
     * Copies a file from a URI to internal storage.
     *
     * @param source the source URI (content://, file://)
     * @param attachmentId the attachment ID to use as filename
     * @return the local file absolute path
     */
    fun copyToStorage(
        source: String,
        attachmentId: String,
    ): String

    /**
     * Streams raw bytes to local storage for the given [attachmentId].
     *
     * @param attachmentId the attachment ID used as filename
     * @param inputStream the file content to copy
     * @return the absolute path to the written file
     */
    fun writeStream(
        attachmentId: String,
        inputStream: InputStream,
    ): String

    /**
     * Opens a caller-managed write stream for [attachmentId]. The caller must close the returned
     * [AttachmentWriteTarget] after the stream write finishes.
     */
    fun openWriteStream(attachmentId: String): AttachmentWriteTarget

    /**
     * Returns the local [File] for the given [attachmentId].
     */
    fun getFile(attachmentId: String): File

    /**
     * Deletes the local file for the given [attachmentId].
     */
    fun deleteFile(attachmentId: String)
}
