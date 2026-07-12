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
            !serialized.contains("**"),
        )
    }

    @Test
    fun mixedDocumentRoundTrips() {
        val markdown =
            "# Title\n" +
                "a **bold** and *italic* line\n" +
                "## Section\n" +
                "plain text"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun roundTripIsIdempotent() {
        val markdown =
            "# Title\n" +
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
        val serialized =
            MarkdownFormatter.toMarkdown(
                MarkdownFormatter.fromMarkdown("\\# not a heading"),
            )
        assertEquals("\\# not a heading", serialized)
    }

    @Test
    fun strikethroughRoundTrips() {
        val markdown = "a ~~strike~~ b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun underlineRoundTrips() {
        val markdown = "a <u>under</u> b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun nestedInlineWithStrikeAndUnderlineRoundTrips() {
        val markdown = "**bold ~~struck~~** and <u>lined</u>"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun uncheckedChecklistRoundTrips() {
        val markdown = "- [ ] buy milk"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun checkedChecklistRoundTrips() {
        val markdown = "- [x] done"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun mixedChecklistDocumentRoundTrips() {
        val markdown = "- [ ] a\n- [x] b\nplain"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun checklistItemWithInlineFormattingRoundTrips() {
        val markdown = "- [ ] a **bold** item"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun literalTildeIsEscaped() {
        val markdown = "a \\~ b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun literalAngleBracketIsEscaped() {
        val markdown = "a \\< b"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun plainChecklistLookAlikeIsNotParsedAsChecklist() {
        // A plain paragraph typed as "- [ ] x" must survive as text (its brackets are escaped).
        val serialized = roundTrip("- \\[ \\] x")
        assertEquals("- \\[ \\] x", serialized)
    }

    @Test
    fun emptyLineAfterChecklistIsNotSerializedAsChecklist() {
        // The trailing empty line must stay empty, not inherit the checklist span at the boundary.
        val markdown = "- [ ] a\n"
        assertEquals(markdown, roundTrip(markdown))
    }

    @Test
    fun documentWithEveryFeatureIsIdempotent() {
        val markdown =
            "# Title\n" +
                "a **bold** ~~struck~~ <u>lined</u> line\n" +
                "- [ ] todo\n" +
                "- [x] done"
        val once = roundTrip(markdown)
        val twice = roundTrip(once)
        assertEquals(markdown, once)
        assertEquals("Round trip must be stable", once, twice)
    }
}
