package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents the lifecycle status of a note.
 *
 * @property ACTIVE the note is visible and editable
 * @property TRASH the note has been moved to trash (soft-deleted)
 * @property ARCHIVE the note has been archived
 */
enum class NoteStatus(val value: Int) {
    ACTIVE(0),
    TRASH(1),
    ARCHIVE(2),

    ;

    companion object {
        /**
         * Converts value to NoteStatus. Fallback to ACTIVE if value is unknown
         */
        fun fromValue(value: Int): NoteStatus {
            return entries.find { it.value == value } ?: ACTIVE
        }
    }
}
