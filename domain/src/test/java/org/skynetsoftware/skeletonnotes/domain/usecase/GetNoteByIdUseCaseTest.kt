package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.BaseFakeNotesRepository
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

class GetNoteByIdUseCaseTest {
    @Test
    fun loadsNoteById() =
        runTest {
            val expectedNote =
                Note(
                    id = "5",
                    title = "Test",
                    content = "Content",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = setOf("tag"),
                )
            val expectedAttachments = listOf(Attachment("1", "5", "file://test.txt"))
            val repository = FakeGetByIdRepository(expectedNote, expectedAttachments)
            val useCase = GetNoteByIdUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals("5", data.note.id)
            assertEquals("Test", data.note.title)
            assertEquals(setOf("tag"), data.note.tags)
            assertEquals(1, data.attachments.size)
        }

    @Test
    fun returnsErrorWhenNotFound() =
        runTest {
            val repository = FakeGetByIdRepository(null, emptyList(), shouldFail = true)
            val useCase = GetNoteByIdUseCase(repository)

            val result = useCase("999")
            assertTrue(result is Result.Failure)
        }

    @Test
    fun loadsNoteWithoutAttachments() =
        runTest {
            val note =
                Note(
                    id = "1",
                    title = "Solo",
                    content = "Just content",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = emptySet(),
                )
            val repository = FakeGetByIdRepository(note, emptyList())
            val useCase = GetNoteByIdUseCase(repository)

            val result = useCase("1")
            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertTrue(data.attachments.isEmpty())
        }

    private class FakeGetByIdRepository(
        private val note: Note?,
        private val attachments: List<Attachment>,
        private val shouldFail: Boolean = false,
    ) : BaseFakeNotesRepository() {
        override fun getNoteById(id: String): Result<NoteWithAttachments> {
            if (shouldFail) return Result.Failure(RuntimeException("Not found"))
            return Result.Success(NoteWithAttachments(note!!, attachments))
        }
    }
}
