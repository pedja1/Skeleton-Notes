package org.skynetsoftware.skeletonnotes.data.attachment

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import java.io.File
import java.io.InputStream

/**
 * Manages local storage of attachment files. Copies content:// URIs
 * to internal storage so files are available for sync and across reboots.
 */
internal class AttachmentStorageImpl(
    private val context: Context,
) : AttachmentFileStorage {
    private val attachmentsDir: File by lazy {
        File(context.filesDir, "attachments").apply { mkdirs() }
    }

    /**
     * Copies a file from a URI to internal storage.
     *
     * @param source the source URI (content://, file://)
     * @param attachmentId the attachment ID to use as filename
     * @param mimeType mimeType of the source, to determine file extension
     * @return the local file absolute path
     */
    @SuppressLint("UseKtx")
    override fun copyToStorage(
        source: String,
        attachmentId: String,
        mimeType: String?,
    ): String {
        val sourceUri = Uri.parse(source)
        val ext =
            mimeType
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?.let { ".$it" }
        val file = attachmentFile(attachmentId, ext)
        val source = context.contentResolver.openInputStream(sourceUri) ?: error("Failed to open source")
        source.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file.absolutePath
    }

    /**
     * Returns the local file for the given [attachmentId], resolving any extension
     * the file may have been stored with (e.g. {uuid}.jpg).
     */
    override fun getFile(attachmentId: String): File {
        require(isSafeAttachmentId(attachmentId)) { "Unsafe attachment id" }
        return attachmentsDir
            .listFiles { f ->
                f.name == attachmentId || f.name.startsWith("$attachmentId.")
            }?.firstOrNull() ?: File(attachmentsDir, attachmentId)
    }

    /**
     * Streams raw bytes to internal storage for the given [attachmentId].
     *
     * @param attachmentId the attachment ID to use as filename
     * @param inputStream the file content to copy
     * @return the local file absolute path
     */
    override fun writeStream(
        attachmentId: String,
        inputStream: InputStream,
    ): String =
        openWriteStream(attachmentId).use { target ->
            inputStream.copyTo(target.outputStream)
            target.path
        }

    /** Opens internal storage for caller-managed streaming into [attachmentId]. */
    override fun openWriteStream(
        attachmentId: String,
        extension: String?,
    ): AttachmentWriteTarget {
        val file = attachmentFile(attachmentId, extension)
        return AttachmentWriteTarget(file.absolutePath, file.outputStream())
    }

    /**
     * Deletes the local file for the given [attachmentId], resolving any extension the file may
     * have been stored with (e.g. {uuid}.jpg), mirroring the lookup in [getFile].
     */
    override fun deleteFile(attachmentId: String) {
        require(isSafeAttachmentId(attachmentId)) { "Unsafe attachment id" }
        attachmentsDir
            .listFiles { f ->
                f.name == attachmentId || f.name.startsWith("$attachmentId.")
            }?.forEach { it.delete() }
    }

    /** Returns a file for [attachmentId] (plus optional [extension]) after enforcing attachment-directory containment. */
    private fun attachmentFile(
        attachmentId: String,
        extension: String? = null,
    ): File {
        require(isSafeAttachmentId(attachmentId)) { "Unsafe attachment id" }
        val filename = "$attachmentId${extension.orEmpty()}"
        val directory = attachmentsDir.canonicalFile
        val file = File(directory, filename).canonicalFile
        require(file.parentFile == directory) { "Unsafe attachment id" }
        return file
    }

    /** Returns true when [attachmentId] can only name a direct child file. */
    private fun isSafeAttachmentId(attachmentId: String): Boolean =
        attachmentId.isNotBlank() &&
            !attachmentId.contains('/') &&
            !attachmentId.contains('\\') &&
            !attachmentId.contains("..")
}
