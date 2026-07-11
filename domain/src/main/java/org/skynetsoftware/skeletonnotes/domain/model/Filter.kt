package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Data class holding note list filter options
 */
data class Filter(
    val query: String,
    val showActive: Boolean,
    val showTrashed: Boolean,
    val showArchived: Boolean,
)
