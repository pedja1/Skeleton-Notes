package org.skynetsoftware.skeletonnotes.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

class SearchAndFilterNotesUseCaseTest {
    private val useCase = SearchAndFilterNotesUseCase()

    @Test
    fun searchMatchesTitle() {
        val notes =
            listOf(
                note(1, "Hello World", "Some content"),
                note(2, "Goodbye", "Other content"),
            )
        val result = useCase(notes, "Hello", includeTrash = false, includeArchived = false)
        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].title)
    }

    @Test
    fun searchMatchesContent() {
        val notes =
            listOf(
                note(1, "Note A", "Contains keyword here"),
                note(2, "Note B", "No match"),
            )
        val result = useCase(notes, "keyword", includeTrash = false, includeArchived = false)
        assertEquals(1, result.size)
        assertEquals("Note A", result[0].title)
    }

    @Test
    fun excludesTrashByDefault() {
        val notes =
            listOf(
                note(1, "Active", status = NoteStatus.ACTIVE),
                note(2, "Trash", status = NoteStatus.TRASH),
            )
        val result = useCase(notes, "", includeTrash = false, includeArchived = false)
        assertEquals(1, result.size)
        assertEquals("Active", result[0].title)
    }

    @Test
    fun includesTrashWhenRequested() {
        val notes =
            listOf(
                note(1, "Active", status = NoteStatus.ACTIVE),
                note(2, "Trash", status = NoteStatus.TRASH),
            )
        val result = useCase(notes, "", includeTrash = true, includeArchived = false)
        assertEquals(2, result.size)
    }

    @Test
    fun includesArchivedWhenRequested() {
        val notes =
            listOf(
                note(1, "Active", status = NoteStatus.ACTIVE),
                note(2, "Archived", status = NoteStatus.ARCHIVE),
            )
        val result = useCase(notes, "", includeTrash = false, includeArchived = true)
        assertEquals(2, result.size)
    }

    @Test
    fun emptyQueryReturnsAllNotes() {
        val notes =
            listOf(
                note(1, "A"),
                note(2, "B"),
            )
        val result = useCase(notes, "", includeTrash = false, includeArchived = false)
        assertEquals(2, result.size)
    }

    @Test
    fun searchIsCaseInsensitive() {
        val notes =
            listOf(
                note(1, "UPPERCASE"),
                note(2, "lowercase"),
            )
        val result = useCase(notes, "upper", includeTrash = false, includeArchived = false)
        assertEquals(1, result.size)
        assertEquals("UPPERCASE", result[0].title)
    }

    private fun note(
        id: Long,
        title: String,
        content: String = "Content",
        createdAt: Long = 0L,
        modifiedAt: Long = 0L,
        status: NoteStatus = NoteStatus.ACTIVE,
    ) = Note(id, title, content, createdAt, modifiedAt, emptySet(), status)
}
