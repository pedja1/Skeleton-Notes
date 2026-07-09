package org.skynetsoftware.skeletonnotes.home

import android.content.Context
import org.skynetsoftware.skeletonnotes.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Categories returned by [NoteTimeFormatter.categorize], used to produce
 * a human-readable timestamp string for note cards.
 */
enum class TimeCategory {
    /** Today: display time only (e.g. "14:30"). */
    TODAY,
    /** Yesterday: display "Yesterday". */
    YESTERDAY,
    /** Within the last 7 days (excluding today and yesterday): display "Last Week". */
    LAST_WEEK,
    /** This year older than 7 days: display month only (e.g. "Jan"). */
    THIS_YEAR,
    /** More than 1 year ago: display year only (e.g. "2025"). */
    OLDER
}

/**
 * Formats the [modifiedAt] timestamp of a note into a human-readable string
 * suitable for display on note cards.
 *
 * Formatting rules:
 * - Today: time only (e.g. "14:30")
 * - Yesterday: "Yesterday"
 * - Within last 7 days (excluding today and yesterday): "Last Week"
 * - This year, older than 7 days: month only (e.g. "Jan")
 * - More than 1 year ago: year only (e.g. "2025")
 */
object NoteTimeFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    /**
     * Categorizes the given [modifiedAt] timestamp (epoch millis) into a [TimeCategory].
     */
    fun categorize(modifiedAt: Long, now: Calendar = Calendar.getInstance()): TimeCategory {
        val then = Calendar.getInstance().apply { timeInMillis = modifiedAt }

        if (isSameDay(now, then)) return TimeCategory.TODAY

        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (isSameDay(yesterday, then)) return TimeCategory.YESTERDAY

        val sevenDaysAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
        if (then.after(sevenDaysAgo)) return TimeCategory.LAST_WEEK

        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) return TimeCategory.THIS_YEAR

        return TimeCategory.OLDER
    }

    /**
     * Formats the given [modifiedAt] timestamp (epoch millis) into a display string
     * using the provided [context] for localized resources.
     */
    fun format(modifiedAt: Long, context: Context, now: Calendar = Calendar.getInstance()): String {
        val category = categorize(modifiedAt, now)
        return when (category) {
            TimeCategory.TODAY -> timeFormat.format(Date(modifiedAt))
            TimeCategory.YESTERDAY -> context.getString(R.string.note_last_edited_yesterday)
            TimeCategory.LAST_WEEK -> context.getString(R.string.note_last_edited_last_week)
            TimeCategory.THIS_YEAR -> monthFormat.format(Date(modifiedAt))
            TimeCategory.OLDER -> {
                val then = Calendar.getInstance().apply { timeInMillis = modifiedAt }
                then.get(Calendar.YEAR).toString()
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
