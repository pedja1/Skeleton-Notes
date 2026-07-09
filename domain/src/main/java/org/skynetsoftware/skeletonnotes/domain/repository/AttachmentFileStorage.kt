package org.skynetsoftware.skeletonnotes.domain.repository

import java.io.File

/**
 * Abstraction for local attachment file storage, allowing the sync use case
 * to read and write attachment bytes without depending on Android specifics.
 */
interface AttachmentFileStorage {
    /**
     * Writes raw bytes to local storage for the given [attachmentId].
     *
     * @param attachmentId the attachment ID used as filename
     * @param bytes the file content to write
     * @return the absolute path to the written file
     */
    fun writeBytes(
        attachmentId: String,
        bytes: ByteArray,
    ): String

    /**
     * Returns the local [File] for the given [attachmentId].
     */
    fun getFile(attachmentId: String): File

    /**
     * Deletes the local file for the given [attachmentId].
     */
    fun deleteFile(attachmentId: String)
}
