package org.skynetsoftware.skeletonnotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.note.MarkdownFormatter

/**
 * Guards the load/save round trip in [MarkdownFormatter] so that parsing Markdown into the editor's
 * span model and serializing it back is stable and preserves the parity feature set (bold, italic,
 * H1, H2, images). Uses instrumentation because [MarkdownFormatter] depends on Android's span
 * classes and [android.text.Html].
 */
@RunWith(AndroidJUnit4::class)
class MarkdownFormatterInstrumentedTest {

    private fun roundTrip(markdown: String): String =
        MarkdownFormatter.toMarkdown(MarkdownFormatter.fromMarkdown(markdown))

    @Test
    fun plainParagraphsRoundTrip() {
        val markdown = "A\nB\nC"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun boldRoundTrips() {
        val markdown = "a **bold** b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun italicRoundTrips() {
        val markdown = "a *italic* b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun nestedBoldItalicRoundTrips() {
        val markdown = "**bold *both* bold**"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun h1RoundTrips() {
        val markdown = "# Heading"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun h2RoundTrips() {
        val markdown = "## Sub heading"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun headingDoesNotEmitRedundantBold() {
        val serialized = roundTrip("# Heading")
        assertTrue(
            "Heading must not contain inline bold markers, but was: $serialized",
            !serialized.contains("**")
        )
    }

    @Test
    fun mixedDocumentRoundTrips() {
        val markdown = "# Title\n" +
            "a **bold** and *italic* line\n" +
            "## Section\n" +
            "plain text"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun roundTripIsIdempotent() {
        val markdown = "# Title\n" +
            "**b** *i*\n" +
            "## Sub\n" +
            "plain"
        val once = roundTrip(markdown)
        val twice = roundTrip(once)
        assertEquals("Round trip must be stable", once, twice)
    }

    @Test
    fun blankLinesArePreserved() {
        val markdown = "A\n\nB"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun leadingHashInPlainParagraphIsEscaped() {
        // A plain paragraph beginning with '#' must not be misread as a heading on the next load.
        val serialized = MarkdownFormatter.toMarkdown(
            MarkdownFormatter.fromMarkdown("\\# not a heading")
        )
        assertEquals("\\# not a heading", serialized)
    }
}
