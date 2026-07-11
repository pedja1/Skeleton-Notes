package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class DeleteAttachmentLocalUseCaseTest {
    @Test
    fun deletesFileForGivenAttachmentId() =
        runTest {
            val storage = TrackingFakeStorage()
            val useCase = DeleteAttachmentLocalUseCase(storage)

            useCase("abc-123")

            assertEquals(listOf("abc-123"), storage.deletedIds)
        }

    @Test
    fun deletesMultipleFilesInSequence() =
        runTest {
            val storage = TrackingFakeStorage()
            val useCase = DeleteAttachmentLocalUseCase(storage)

            useCase("id-1")
            useCase("id-2")

            assertEquals(listOf("id-1", "id-2"), storage.deletedIds)
        }

    private class TrackingFakeStorage : AttachmentFileStorage {
        val deletedIds = mutableListOf<String>()

        override fun copyToStorage(
            source: String,
            attachmentId: String,
            mimeType: String?,
        ) = ""

        override fun writeStream(
            attachmentId: String,
            inputStream: InputStream,
        ) = ""

        override fun openWriteStream(
            attachmentId: String,
            extension: String?,
        ) = AttachmentWriteTarget("", ByteArrayOutputStream())

        override fun getFile(attachmentId: String) = File("")

        override fun deleteFile(attachmentId: String) {
            deletedIds.add(attachmentId)
        }
    }
}
