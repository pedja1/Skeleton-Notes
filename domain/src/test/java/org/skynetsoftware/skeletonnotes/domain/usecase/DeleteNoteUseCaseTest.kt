package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.BaseFakeNotesRepository
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class DeleteNoteUseCaseTest {
    @Test
    fun deletesNoteSuccessfully() =
        runTest {
            val repository = FakeDeleteRepository()
            val useCase = createUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Success)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeDeleteRepository(shouldFail = true)
            val useCase = createUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Failure)
        }

    @Test
    fun deletesAndCanBeCalledMultipleTimes() =
        runTest {
            val repository = FakeDeleteRepository()
            val useCase = createUseCase(repository)

            val result1 = useCase("1")
            val result2 = useCase("2")
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
        }

    @Test
    fun deletesAttachmentFilesWithTheNote() =
        runTest {
            // The attachment rows are the only reference to the files; deleting the note without
            // deleting the files would leak them forever.
            val repository =
                FakeDeleteRepository(
                    attachments = listOf(Attachment("att1", "5", "/fake/att1"), Attachment("att2", "5", "/fake/att2")),
                )
            val storage = TrackingAttachmentStorage()
            val useCase = createUseCase(repository, storage)

            val result = useCase("5")

            assertTrue(result is Result.Success)
            assertEquals(listOf("att1", "att2"), storage.deletedIds)
        }

    @Test
    fun keepsAttachmentFilesWhenDeleteFails() =
        runTest {
            val repository =
                FakeDeleteRepository(
                    shouldFail = true,
                    attachments = listOf(Attachment("att1", "5", "/fake/att1")),
                )
            val storage = TrackingAttachmentStorage()
            val useCase = createUseCase(repository, storage)

            val result = useCase("5")

            assertTrue(result is Result.Failure)
            assertTrue(storage.deletedIds.isEmpty())
        }

    private fun createUseCase(
        repository: FakeDeleteRepository,
        storage: AttachmentFileStorage = TrackingAttachmentStorage(),
    ): DeleteNoteUseCase =
        DeleteNoteUseCase(
            repository,
            DeleteAttachmentLocalUseCase(storage, UnconfinedTestDispatcher()),
        )

    private class FakeDeleteRepository(
        private val shouldFail: Boolean = false,
        private val attachments: List<Attachment> = emptyList(),
    ) : BaseFakeNotesRepository() {
        override suspend fun getNoteById(id: String): Result<NoteWithAttachments> =
            Result.Success(
                NoteWithAttachments(
                    Note(id, "Title", "Content", 1000L, 1000L, emptySet()),
                    attachments,
                ),
            )

        override suspend fun deleteNote(id: String): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("Delete error"))
            return Result.Success(Unit)
        }
    }

    private class TrackingAttachmentStorage : AttachmentFileStorage {
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
