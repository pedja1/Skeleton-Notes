package org.skynetsoftware.skeletonnotes.domain.di

import org.skynetsoftware.skeletonnotes.domain.repository.AttachmentFileStorage
import java.io.File

/**
 * No-op [AttachmentFileStorage] that returns an empty temporary file for every request.
 * Used as a default when no real storage is available (e.g. in tests that don't exercise
 * attachment I/O).
 */
internal object NoOpAttachmentFileStorage : AttachmentFileStorage {
    private val tempFile: File by lazy {
        File.createTempFile("noop_attachment", ".tmp").also { it.deleteOnExit() }
    }

    override fun writeBytes(
        attachmentId: String,
        bytes: ByteArray,
    ): String = tempFile.absolutePath

    override fun getFile(attachmentId: String): File = tempFile

    override fun deleteFile(attachmentId: String) {}
}
