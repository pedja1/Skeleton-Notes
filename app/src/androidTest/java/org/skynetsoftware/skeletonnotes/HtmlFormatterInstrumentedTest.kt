package org.skynetsoftware.skeletonnotes

import android.text.Html
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.note.HtmlFormatter

/**
 * Guards the load/save round trip in [HtmlFormatter] against the corruption that used to grow
 * blank paragraphs and nested `<b>` tags on every open (see NoteDetailActivity load/save path).
 * Uses instrumentation because [HtmlFormatter] depends on [android.text.Html].
 */
@RunWith(AndroidJUnit4::class)
class HtmlFormatterInstrumentedTest {

    private val noImages = Html.ImageGetter { null }

    private fun roundTrip(html: String): String =
        HtmlFormatter.toHtml(HtmlFormatter.fromHtml(html, noImages))

    @Test
    fun roundTripIsIdempotent() {
        val html = "<p dir=\"ltr\">A</p>\n" +
            "<p dir=\"ltr\">B</p>\n" +
            "<h1 dir=\"ltr\">Ssss</h1>\n" +
            "<p dir=\"ltr\"><br></p>\n"

        val once = roundTrip(html)
        val twice = roundTrip(once)
        assertEquals("Round trip must be stable after normalization", once, twice)
    }

    @Test
    fun blankParagraphsDoNotGrow() {
        val html = "<p dir=\"ltr\">A</p>\n<p dir=\"ltr\">B</p>\n"

        val once = roundTrip(html)
        val twice = roundTrip(once)

        val blankRegex = Regex("<p dir=\"ltr\"><br></p>")
        assertEquals(
            "Blank paragraph count must not grow across round trips",
            blankRegex.findAll(once).count(),
            blankRegex.findAll(twice).count()
        )
    }

    @Test
    fun headingBoldDoesNotAccumulate() {
        val corrupted = "<h1 dir=\"ltr\"><b><b>Ssss</b></b></h1>\n"

        val healed = roundTrip(corrupted)

        assertTrue(
            "Heading should serialize without redundant <b>, but was: $healed",
            healed.contains("<h1 dir=\"ltr\">Ssss</h1>")
        )
        // Re-saving must keep it clean.
        assertEquals(healed, roundTrip(healed))
    }
}
