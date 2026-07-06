package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class GetAllNotesUseCaseTest {

    @Test
    fun returnsNotesFromRepository() = runTest {
        val notes = listOf(
            Note(id = 1, title = "First", content = "# First\nContent", createdAt = 1000L, modifiedAt = 1000L, tags = emptySet()),
            Note(id = 2, title = "Second", content = "# Second\nContent", createdAt = 2000L, modifiedAt = 2000L, tags = emptySet())
        )
        val repository = FakeNotesRepository(notes)
        val useCase = GetAllNotesUseCase(repository)

        val result = useCase().first()
        val notesList = (result as Result.Success).data

        assertEquals(2, notesList.size)
        assertEquals("First", notesList[0].title)
        assertEquals("Second", notesList[1].title)
    }

    @Test
    fun returnsEmptyListWhenRepositoryHasNoNotes() = runTest {
        val repository = FakeNotesRepository(emptyList())
        val useCase = GetAllNotesUseCase(repository)

        val result = useCase().first()
        val notesList = (result as Result.Success).data

        assertTrue(notesList.isEmpty())
    }

    private class FakeNotesRepository(private val notes: List<Note>) : NotesRepository {
        override suspend fun getAllNotes(): Result<List<Note>> = Result.Success(notes)
        override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> =
            Result.Success(NoteWithAttachments(note = notes.first { it.id == id }, attachments = emptyList()))
        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> =
            Result.Success(noteWithAttachments.note.id)
        override suspend fun deleteNote(id: Long): Result<Unit> = Result.Success(Unit)
    }
}
