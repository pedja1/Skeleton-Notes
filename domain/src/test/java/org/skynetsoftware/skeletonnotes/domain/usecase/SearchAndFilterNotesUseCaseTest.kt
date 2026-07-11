package org.skynetsoftware.skeletonnotes.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Filter
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

class SearchAndFilterNotesUseCaseTest {
    private val useCase = SearchAndFilterNotesUseCase()

    @Test
    fun searchMatchesTitle() {
        val notes =
            listOf(
                note("1", "Hello World", "Some content"),
                note("2", "Goodbye", "Other content"),
            )
        val result = useCase(notes, filter(query = "Hello"))
        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].note.title)
    }

    @Test
    fun searchMatchesContent() {
        val notes =
            listOf(
                note("1", "Note A", "Contains keyword here"),
                note("2", "Note B", "No match"),
            )
        val result = useCase(notes, filter(query = "keyword"))
        assertEquals(1, result.size)
        assertEquals("Note A", result[0].note.title)
    }

    @Test
    fun excludesTrashByDefault() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Trash", status = NoteStatus.TRASH),
            )
        val result = useCase(notes, filter())
        assertEquals(1, result.size)
        assertEquals("Active", result[0].note.title)
    }

    @Test
    fun includesTrashWhenRequested() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Trash", status = NoteStatus.TRASH),
            )
        val result = useCase(notes, filter(showTrashed = true))
        assertEquals(2, result.size)
    }

    @Test
    fun includesArchivedWhenRequested() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Archived", status = NoteStatus.ARCHIVE),
            )
        val result = useCase(notes, filter(showArchived = true))
        assertEquals(2, result.size)
    }

    @Test
    fun showsOnlyTrashWhenActiveDisabled() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Trash", status = NoteStatus.TRASH),
                note("3", "Archived", status = NoteStatus.ARCHIVE),
            )
        val result = useCase(notes, filter(showActive = false, showTrashed = true))
        assertEquals(1, result.size)
        assertEquals("Trash", result[0].note.title)
    }

    @Test
    fun showsOnlyArchivedWhenActiveDisabled() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Trash", status = NoteStatus.TRASH),
                note("3", "Archived", status = NoteStatus.ARCHIVE),
            )
        val result = useCase(notes, filter(showActive = false, showArchived = true))
        assertEquals(1, result.size)
        assertEquals("Archived", result[0].note.title)
    }

    @Test
    fun returnsEmptyWhenNoStatusEnabled() {
        val notes =
            listOf(
                note("1", "Active", status = NoteStatus.ACTIVE),
                note("2", "Trash", status = NoteStatus.TRASH),
                note("3", "Archived", status = NoteStatus.ARCHIVE),
            )
        val result = useCase(notes, filter(showActive = false))
        assertTrue(result.isEmpty())
    }

    @Test
    fun searchAppliesWithinSelectedStatus() {
        val notes =
            listOf(
                note("1", "Keep me", status = NoteStatus.TRASH),
                note("2", "Drop me", status = NoteStatus.TRASH),
                note("3", "Keep me", status = NoteStatus.ACTIVE),
            )
        val result = useCase(notes, filter(query = "Keep", showActive = false, showTrashed = true))
        assertEquals(1, result.size)
        assertEquals("1", result[0].note.id)
    }

    @Test
    fun emptyQueryReturnsAllNotes() {
        val notes =
            listOf(
                note("1", "A"),
                note("2", "B"),
            )
        val result = useCase(notes, filter())
        assertEquals(2, result.size)
    }

    @Test
    fun searchIsCaseInsensitive() {
        val notes =
            listOf(
                note("1", "UPPERCASE"),
                note("2", "lowercase"),
            )
        val result = useCase(notes, filter(query = "upper"))
        assertEquals(1, result.size)
        assertEquals("UPPERCASE", result[0].note.title)
    }

    private fun filter(
        query: String = "",
        showActive: Boolean = true,
        showTrashed: Boolean = false,
        showArchived: Boolean = false,
    ) = Filter(query = query, showActive = showActive, showTrashed = showTrashed, showArchived = showArchived)

    private fun note(
        id: String,
        title: String,
        content: String = "Content",
        createdAt: Long = 0L,
        modifiedAt: Long = 0L,
        status: NoteStatus = NoteStatus.ACTIVE,
    ) = NoteWithAttachments(Note(id, title, content, createdAt, modifiedAt, emptySet(), status), emptyList())
}
