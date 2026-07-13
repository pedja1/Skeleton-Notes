package org.skynetsoftware.skeletonnotes.settings

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary

/**
 * Which long-running data operation, if any, is currently in progress. Drives the
 * per-row progress indicators on the import/export settings items.
 */
enum class DataOperation { NONE, EXPORT, IMPORT }

/**
 * Live progress of the running import/export, rendered as `current/total` in the settings item
 * subtitle. [current] is the number of notes processed so far, [total] the number of notes in the
 * operation.
 */
data class DataTransferProgress(
    val current: Int,
    val total: Int,
)

/**
 * One-shot events emitted by [SettingsViewModel] for completed import/export operations.
 */
sealed interface DataTransferEvent {
    /** Export finished; [count] notes were written to the archive. */
    data class ExportSuccess(
        val count: Int,
    ) : DataTransferEvent

    /** Import finished with the given [summary]. */
    data class ImportSuccess(
        val summary: ImportSummary,
    ) : DataTransferEvent

    /** An export or import failed. */
    data object Error : DataTransferEvent
}

/**
 * An incoming note ([incoming]) collides with an [existing] local note; the activity must prompt
 * the user and report the choice back via [SettingsViewModel.onConflictResolved].
 */
data class ImportConflictPrompt(
    val existing: Note,
    val incoming: Note,
)
