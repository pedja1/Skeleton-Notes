package org.skynetsoftware.skeletonnotes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.home.NoteTimeFormatter
import org.skynetsoftware.skeletonnotes.home.TimeCategory
import java.util.Calendar

class NoteTimeFormatterTest {

    private fun calendar(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun categorizeReturnsTodayForSameDay() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JULY, 9, 10, 15).timeInMillis

        assertEquals(TimeCategory.TODAY, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsTodayForSameDayDifferentHour() {
        val now = calendar(2026, Calendar.JULY, 9, 23, 59)
        val modifiedAt = calendar(2026, Calendar.JULY, 9, 0, 1).timeInMillis

        assertEquals(TimeCategory.TODAY, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsYesterdayForPreviousDay() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JULY, 8, 10, 15).timeInMillis

        assertEquals(TimeCategory.YESTERDAY, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsYesterdayAcrossMonthBoundary() {
        val now = calendar(2026, Calendar.JULY, 1, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JUNE, 30, 10, 15).timeInMillis

        assertEquals(TimeCategory.YESTERDAY, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsYesterdayAcrossYearBoundary() {
        val now = calendar(2026, Calendar.JANUARY, 1, 14, 30)
        val modifiedAt = calendar(2025, Calendar.DECEMBER, 31, 10, 15).timeInMillis

        assertEquals(TimeCategory.YESTERDAY, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsLastWeekForTwoDaysAgo() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JULY, 7, 10, 15).timeInMillis

        assertEquals(TimeCategory.LAST_WEEK, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsLastWeekForSixDaysAgo() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JULY, 3, 10, 15).timeInMillis

        assertEquals(TimeCategory.LAST_WEEK, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsThisYearForExactlySevenDaysAgo() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JULY, 2, 10, 15).timeInMillis

        assertEquals(TimeCategory.THIS_YEAR, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsThisYearForOlderInSameYear() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2026, Calendar.JANUARY, 1, 10, 15).timeInMillis

        assertEquals(TimeCategory.THIS_YEAR, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsOlderForPreviousYear() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2025, Calendar.DECEMBER, 31, 10, 15).timeInMillis

        assertEquals(TimeCategory.OLDER, NoteTimeFormatter.categorize(modifiedAt, now))
    }

    @Test
    fun categorizeReturnsOlderForManyYearsAgo() {
        val now = calendar(2026, Calendar.JULY, 9, 14, 30)
        val modifiedAt = calendar(2020, Calendar.JANUARY, 1, 10, 15).timeInMillis

        assertEquals(TimeCategory.OLDER, NoteTimeFormatter.categorize(modifiedAt, now))
    }
}
