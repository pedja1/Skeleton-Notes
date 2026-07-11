package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Filter
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments

/**
 * Use case for searching notes with filtering.
 */
class SearchAndFilterNotesUseCase {
    /**
     * Filters list of notes by search query and enabled statuses
     * @return Filtered list of notes
     */
    operator fun invoke(
        notes: List<NoteWithAttachments>,
        filter: Filter,
    ): List<NoteWithAttachments> =
        notes.filter { noteWithAttachments ->
            val note = noteWithAttachments.note
            val statusFilterMatches =
                when (note.status) {
                    NoteStatus.ACTIVE -> true
                    NoteStatus.TRASH -> filter.showTrashed
                    NoteStatus.ARCHIVE -> filter.showArchived
                }
            val queryFilterMatches =
                if (filter.query.isNotBlank()) {
                    val titleContainsQuery = note.title?.lowercase()?.contains(filter.query.lowercase()) ?: false
                    val contentContainsQuery = note.content.lowercase().contains(filter.query.lowercase())
                    titleContainsQuery || contentContainsQuery
                } else {
                    true
                }
            statusFilterMatches && queryFilterMatches
        }
}
