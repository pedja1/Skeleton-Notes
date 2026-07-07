package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

/**
 * Use case for searching notes with filtering.
 */
class SearchAndFilterNotesUseCase {
    /**
     * Filters list of notes by search query and enabled statuses
     * @return Filtered list of notes
     */
    operator fun invoke(
        notes: List<Note>,
        query: String,
        includeTrash: Boolean,
        includeArchived: Boolean,
    ): List<Note> {
        return notes.filter { note ->
            val statusFilterMatches =
                when (note.status) {
                    NoteStatus.ACTIVE -> true
                    NoteStatus.TRASH -> includeTrash
                    NoteStatus.ARCHIVE -> includeArchived
                }
            val queryFilterMatches =
                if (query.isNotBlank()) {
                    val titleContainsQuery = note.title?.lowercase()?.contains(query.lowercase()) ?: false
                    val contentContainsQuery = note.content.lowercase().contains(query.lowercase())
                    titleContainsQuery || contentContainsQuery
                } else {
                    true
                }
            statusFilterMatches && queryFilterMatches
        }
    }
}
