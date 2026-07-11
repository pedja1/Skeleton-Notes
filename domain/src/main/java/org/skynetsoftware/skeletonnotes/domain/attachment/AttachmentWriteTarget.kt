package org.skynetsoftware.skeletonnotes.domain.attachment

import java.io.OutputStream

/** Open output target for streaming attachment bytes into local storage. */
data class AttachmentWriteTarget(
    /** Absolute path to the file being written. */
    val path: String,
    /** Output stream for the file being written. */
    val outputStream: OutputStream,
) : AutoCloseable {
    /** Closes [outputStream]. */
    override fun close() {
        outputStream.close()
    }
}
