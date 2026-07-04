package org.skynetsoftware.skeletonnotes.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownEditTextTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun setsTextAndAppliesBoldSpan() {
        val view = MarkdownEditText(context)
        view.setText("**bold**")

        val editable = view.text as Spannable
        val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)
        val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }

        assertEquals(1, boldSpans.size)
    }

    @Test
    fun setsTextAndAppliesItalicSpan() {
        val view = MarkdownEditText(context)
        view.setText("*italic*")

        val editable = view.text as Spannable
        val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)
        val italicSpans = styleSpans.filter { it.style == Typeface.ITALIC }

        assertEquals(1, italicSpans.size)
    }

    @Test
    fun setsHeadingTextAndAppliesHeadingSpans() {
        val view = MarkdownEditText(context)
        view.setText("# Title")

        val editable = view.text as Spannable
        val sizeSpans = editable.getSpans(0, editable.length, RelativeSizeSpan::class.java)
        val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)
        val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }

        assertEquals(1, sizeSpans.size)
        assertEquals(1, boldSpans.size)
    }

    @Test
    fun setsStrikethroughText() {
        val view = MarkdownEditText(context)
        view.setText("~~strike~~")

        val editable = view.text as Spannable
        val spans = editable.getSpans(0, editable.length, StrikethroughSpan::class.java)

        assertEquals(1, spans.size)
    }

    @Test
    fun setsCodeTextAppliesMonospace() {
        val view = MarkdownEditText(context)
        view.setText("`code`")

        val editable = view.text as Spannable
        val spans = editable.getSpans(0, editable.length, TypefaceSpan::class.java)

        assertEquals(1, spans.size)
        assertEquals("monospace", spans[0].family)
    }

    @Test
    fun clearsSpansWhenTextBecomesPlain() {
        val view = MarkdownEditText(context)
        view.setText("**bold**")

        val afterBold = view.text as Spannable
        val boldSpans = afterBold.getSpans(0, afterBold.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertEquals(1, boldSpans.size)

        view.setText("plain text")
        val afterPlain = view.text as Spannable
        val remaining = afterPlain.getSpans(0, afterPlain.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertEquals(0, remaining.size)
    }

    @Test
    fun handlesEmptyText() {
        val view = MarkdownEditText(context)
        view.setText("")
        assertTrue(true)
    }

    @Test
    fun handlesMultilineMarkdown() {
        val view = MarkdownEditText(context)
        val text = "# Title\n\n**bold** and *italic*\n\n- item 1\n- item 2"
        view.setText(text)

        val editable = view.text as Spannable
        val sizeSpans = editable.getSpans(0, editable.length, RelativeSizeSpan::class.java)
        val styleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)

        assertTrue(sizeSpans.size >= 1)
        assertTrue(styleSpans.size >= 2)
    }
}
