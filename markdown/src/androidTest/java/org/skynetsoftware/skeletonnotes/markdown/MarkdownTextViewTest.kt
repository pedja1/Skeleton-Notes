package org.skynetsoftware.skeletonnotes.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownTextViewTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun setsTextAndAppliesBoldSpan() {
        val view = MarkdownTextView(context)
        view.text = "**bold**"

        assertEquals("bold", view.text.toString())

        val spannable = view.text as Spannable
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }

        assertEquals(1, boldSpans.size)
        assertEquals(0, spannable.getSpanStart(boldSpans[0]))
        assertEquals(spannable.length, spannable.getSpanEnd(boldSpans[0]))
    }

    @Test
    fun setsTextAndAppliesItalicSpan() {
        val view = MarkdownTextView(context)
        view.text = "*italic*"

        assertEquals("italic", view.text.toString())

        val spannable = view.text as Spannable
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        val italicSpans = styleSpans.filter { it.style == Typeface.ITALIC }

        assertEquals(1, italicSpans.size)
    }

    @Test
    fun setsHeadingTextAndAppliesHeadingSpans() {
        val view = MarkdownTextView(context)
        view.text = "# Title"

        assertEquals("Title", view.text.toString())

        val spannable = view.text as Spannable
        val sizeSpans = spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java)
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }

        assertEquals(1, sizeSpans.size)
        assertEquals(1, boldSpans.size)
        assertEquals(0, spannable.getSpanStart(sizeSpans[0]))
        assertEquals(spannable.length, spannable.getSpanEnd(sizeSpans[0]))
    }

    @Test
    fun setsStrikethroughText() {
        val view = MarkdownTextView(context)
        view.text = "~~strike~~"

        assertEquals("strike", view.text.toString())

        val spannable = view.text as Spannable
        val spans = spannable.getSpans(0, spannable.length, StrikethroughSpan::class.java)

        assertEquals(1, spans.size)
        assertEquals(0, spannable.getSpanStart(spans[0]))
        assertEquals(spannable.length, spannable.getSpanEnd(spans[0]))
    }

    @Test
    fun setsCodeTextAppliesMonospace() {
        val view = MarkdownTextView(context)
        view.text = "`code`"

        assertEquals("code", view.text.toString())

        val spannable = view.text as Spannable
        val typefaceSpans = spannable.getSpans(0, spannable.length, TypefaceSpan::class.java)
        val backgroundSpans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)

        assertEquals(1, typefaceSpans.size)
        assertEquals("monospace", typefaceSpans[0].family)
        assertEquals(1, backgroundSpans.size)
    }

    @Test
    fun setsBlockquoteTextAppliesQuoteAndForegroundSpans() {
        val view = MarkdownTextView(context)
        view.text = "> quoted text"

        assertEquals("quoted text", view.text.toString())

        val spannable = view.text as Spannable
        val quoteSpans = spannable.getSpans(0, spannable.length, QuoteSpan::class.java)
        val foregroundSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)

        assertEquals(1, quoteSpans.size)
        assertEquals(1, foregroundSpans.size)
    }

    @Test
    fun setsBulletListTextAppliesIndent() {
        val view = MarkdownTextView(context)
        view.text = "- item one"

        assertEquals("item one", view.text.toString())

        val spannable = view.text as Spannable
        val indentSpans = spannable.getSpans(0, spannable.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, indentSpans.size)
        assertEquals(0, spannable.getSpanStart(indentSpans[0]))
        assertEquals(spannable.length, spannable.getSpanEnd(indentSpans[0]))
    }

    @Test
    fun setsOrderedListTextAppliesIndent() {
        val view = MarkdownTextView(context)
        view.text = "1. first item"

        assertEquals("first item", view.text.toString())

        val spannable = view.text as Spannable
        val indentSpans = spannable.getSpans(0, spannable.length, LeadingMarginSpan.Standard::class.java)

        assertEquals(1, indentSpans.size)
    }

    @Test
    fun clearsSpansWhenTextBecomesPlain() {
        val view = MarkdownTextView(context)
        view.text = "**bold**"

        assertEquals("bold", view.text.toString())

        val afterBold = view.text as Spannable
        val boldSpans = afterBold.getSpans(0, afterBold.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertEquals(1, boldSpans.size)

        view.text = "plain text"
        assertEquals("plain text", view.text.toString())

        val afterPlain = view.text as Spannable
        val remaining = afterPlain.getSpans(0, afterPlain.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertEquals(0, remaining.size)
    }

    @Test
    fun handlesEmptyText() {
        val view = MarkdownTextView(context)
        view.text = ""
        assertNotNull(view.text)
        assertEquals("", view.text.toString())
    }

    @Test
    fun handlesNullText() {
        val view = MarkdownTextView(context)
        view.text = null
        assertNotNull(view.text)
    }

    @Test
    fun handlesMultilineMarkdown() {
        val view = MarkdownTextView(context)
        val text = "# Title\n\n**bold** and *italic*\n\n- item 1\n- item 2"
        view.text = text

        val displayed = view.text.toString()
        assertTrue(displayed.contains("Title"))
        assertTrue(displayed.contains("bold"))
        assertTrue(displayed.contains("italic"))
        assertTrue(displayed.contains("item 1"))
        assertTrue(displayed.contains("item 2"))
        assertTrue(!displayed.contains("#"))
        assertTrue(!displayed.contains("**"))
        assertTrue(!displayed.contains("*italic*"))

        val spannable = view.text as Spannable
        val sizeSpans = spannable.getSpans(0, spannable.length, RelativeSizeSpan::class.java)
        val styleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)

        assertTrue(sizeSpans.size >= 1)
        assertTrue(styleSpans.size >= 2)
    }
}
