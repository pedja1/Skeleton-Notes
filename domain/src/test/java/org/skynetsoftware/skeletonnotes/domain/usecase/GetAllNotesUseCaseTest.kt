package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class GetAllNotesUseCaseTest {

    @Test
    fun returnsNotesFromRepository() = runTest {
        val notes = listOf(
            Note(id = 1, title = "First", content = "# First\nContent"),
            Note(id = 2, title = "Second", content = "# Second\nContent")
        )
        val repository = FakeNotesRepository(notes)
        val useCase = GetAllNotesUseCase(repository)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals("First", result[0].title)
        assertEquals("Second", result[1].title)
    }

    @Test
    fun returnsEmptyListWhenRepositoryHasNoNotes() = runTest {
        val repository = FakeNotesRepository(emptyList())
        val useCase = GetAllNotesUseCase(repository)

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    private class FakeNotesRepository(private val notes: List<Note>) : NotesRepository {
        override suspend fun getAllNotes(): List<Note> = notes
        override suspend fun getNoteById(id: Long): Note? = notes.find { it.id == id }
        override suspend fun saveNote(note: Note): Long = 1L
        override suspend fun deleteNote(note: Note) {}
    }
}
