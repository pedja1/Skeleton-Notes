package org.skynetsoftware.skeletonnotes.markdown

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanRange
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanType

@RunWith(AndroidJUnit4::class)
class MarkdownSpanApplierTest {

    private val applier = MarkdownSpanApplier()

    @Test
    fun appliesBoldSpan() {
        val text = SpannableStringBuilder("**bold**")
        val spans = listOf(SpanRange(0, text.length, SpanType.BOLD))
        applier.apply(text, spans)

        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)
        assertEquals(Typeface.BOLD, styleSpans[0].style)
    }

    @Test
    fun appliesItalicSpan() {
        val text = SpannableStringBuilder("*italic*")
        val spans = listOf(SpanRange(0, text.length, SpanType.ITALIC))
        applier.apply(text, spans)

        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)
        assertEquals(Typeface.ITALIC, styleSpans[0].style)
    }

    @Test
    fun appliesStrikethroughSpan() {
        val text = SpannableStringBuilder("~~strike~~")
        val spans = listOf(SpanRange(0, text.length, SpanType.STRIKETHROUGH))
        applier.apply(text, spans)

        val strikeSpans = text.getSpans(0, text.length, StrikethroughSpan::class.java)
        assertEquals(1, strikeSpans.size)
    }

    @Test
    fun appliesCodeSpanWithMonospaceAndBackground() {
        val text = SpannableStringBuilder("`code`")
        val spans = listOf(SpanRange(0, text.length, SpanType.CODE))
        applier.apply(text, spans)

        val typefaceSpans = text.getSpans(0, text.length, TypefaceSpan::class.java)
        val bgSpans = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
        assertEquals(1, typefaceSpans.size)
        assertEquals("monospace", typefaceSpans[0].family)
        assertEquals(1, bgSpans.size)
    }

    @Test
    fun appliesHeadingSpansWithRelativeSizeAndBold() {
        val text = SpannableStringBuilder("# Heading")
        val spans = listOf(SpanRange(0, text.length, SpanType.HEADING_1))
        applier.apply(text, spans)

        val sizeSpans = text.getSpans(0, text.length, RelativeSizeSpan::class.java)
        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(1, sizeSpans.size)
        assertEquals(1, styleSpans.size)
        assertTrue(styleSpans[0].style == Typeface.BOLD)
    }

    @Test
    fun appliesBlockquoteWithQuoteAndColorSpans() {
        val text = SpannableStringBuilder("> quote")
        val spans = listOf(SpanRange(0, text.length, SpanType.BLOCKQUOTE))
        applier.apply(text, spans)

        val quoteSpans = text.getSpans(0, text.length, QuoteSpan::class.java)
        val colorSpans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        assertEquals(1, quoteSpans.size)
        assertEquals(1, colorSpans.size)
    }

    @Test
    fun appliesBulletListIndent() {
        val text = SpannableStringBuilder("- item")
        val spans = listOf(SpanRange(0, text.length, SpanType.BULLET_LIST))
        applier.apply(text, spans)

        val marginSpans = text.getSpans(0, text.length, LeadingMarginSpan.Standard::class.java)
        assertEquals(1, marginSpans.size)
    }

    @Test
    fun appliesOrderedListIndent() {
        val text = SpannableStringBuilder("1. item")
        val spans = listOf(SpanRange(0, text.length, SpanType.ORDERED_LIST))
        applier.apply(text, spans)

        val marginSpans = text.getSpans(0, text.length, LeadingMarginSpan.Standard::class.java)
        assertEquals(1, marginSpans.size)
    }

    @Test
    fun removesPreviousSpansBeforeApplyingNewOnes() {
        val text = SpannableStringBuilder("**bold**")
        val spans = listOf(SpanRange(0, text.length, SpanType.BOLD))
        applier.apply(text, spans)

        val spansBefore = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(1, spansBefore.size)

        applier.apply(text, emptyList())

        val spansAfter = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(0, spansAfter.size)
    }

    @Test
    fun emptySpanListClearsAllSpans() {
        val text = SpannableStringBuilder("# Title\n**bold**")
        val spans = listOf(
            SpanRange(0, 7, SpanType.HEADING_1),
            SpanRange(8, text.length, SpanType.BOLD)
        )
        applier.apply(text, spans)

        val before = text.getSpans(0, text.length, Any::class.java)
        assertTrue(before.isNotEmpty())

        applier.apply(text, emptyList())

        val after = text.getSpans(0, text.length, Any::class.java)
            .filter { it.javaClass.name.startsWith("android.text.style") }
        assertEquals(0, after.size)
    }

    @Test
    fun clampsOutOfBoundsRange() {
        val text = SpannableStringBuilder("short")
        val spans = listOf(SpanRange(0, 100, SpanType.BOLD))
        applier.apply(text, spans)

        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)
    }

    @Test
    fun handlesInvalidRangeWhereStartEqualsEnd() {
        val text = SpannableStringBuilder("text")
        val spans = listOf(SpanRange(1, 1, SpanType.BOLD))
        applier.apply(text, spans)

        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        assertEquals(0, styleSpans.size)
    }

    @Test
    fun appliesMultipleHeadingLevelsCorrectly() {
        val h1 = SpannableStringBuilder("# H1")
        val h3 = SpannableStringBuilder("### H3")

        applier.apply(h1, listOf(SpanRange(0, h1.length, SpanType.HEADING_1)))
        applier.apply(h3, listOf(SpanRange(0, h3.length, SpanType.HEADING_3)))

        val r1 = h1.getSpans(0, h1.length, RelativeSizeSpan::class.java)
        val r3 = h3.getSpans(0, h3.length, RelativeSizeSpan::class.java)
        assertEquals(1, r1.size)
        assertEquals(1, r3.size)
        assertNotNull(r1[0])
        assertNotNull(r3[0])
    }
}
