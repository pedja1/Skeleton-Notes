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
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanRange
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanType

class MarkdownSpanApplier {

    fun apply(spannable: Spannable, spans: List<SpanRange>) {
        removeMarkdownSpans(spannable)

        for (span in spans) {
            applySpan(spannable, span)
        }
    }

    private fun removeMarkdownSpans(spannable: Spannable) {
        val length = spannable.length
        val allSpans = spannable.getSpans(0, length, Any::class.java)
        for (span in allSpans) {
            when (span) {
                is RelativeSizeSpan,
                is StyleSpan,
                is StrikethroughSpan,
                is TypefaceSpan,
                is BackgroundColorSpan,
                is QuoteSpan,
                is LeadingMarginSpan.Standard,
                is ForegroundColorSpan -> spannable.removeSpan(span)
            }
        }
    }

    private fun applySpan(spannable: Spannable, range: SpanRange) {
        val start = range.start.coerceIn(0, spannable.length)
        val end = range.end.coerceIn(0, spannable.length)
        if (start >= end) return

        val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

        when (range.type) {
            SpanType.HEADING_1 -> applyHeading(spannable, start, end, 1.6f, flag)
            SpanType.HEADING_2 -> applyHeading(spannable, start, end, 1.4f, flag)
            SpanType.HEADING_3 -> applyHeading(spannable, start, end, 1.25f, flag)
            SpanType.HEADING_4 -> applyHeading(spannable, start, end, 1.1f, flag)
            SpanType.HEADING_5 -> applyHeading(spannable, start, end, 1.0f, flag)
            SpanType.HEADING_6 -> applyHeading(spannable, start, end, 0.9f, flag)
            SpanType.BOLD -> spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
            SpanType.ITALIC -> spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, flag)
            SpanType.STRIKETHROUGH -> spannable.setSpan(StrikethroughSpan(), start, end, flag)
            SpanType.CODE -> {
                spannable.setSpan(TypefaceSpan("monospace"), start, end, flag)
                spannable.setSpan(BackgroundColorSpan(CODE_BG_COLOR), start, end, flag)
            }
            SpanType.BLOCKQUOTE -> {
                spannable.setSpan(QuoteSpan(QUOTE_BAR_COLOR), start, end, flag)
                spannable.setSpan(ForegroundColorSpan(QUOTE_TEXT_COLOR), start, end, flag)
            }
            SpanType.BULLET_LIST -> {
                spannable.setSpan(LeadingMarginSpan.Standard(BULLET_INDENT), start, end, flag)
            }
            SpanType.ORDERED_LIST -> {
                spannable.setSpan(LeadingMarginSpan.Standard(BULLET_INDENT), start, end, flag)
            }
        }
    }

    private fun applyHeading(
        spannable: Spannable,
        start: Int,
        end: Int,
        scale: Float,
        flag: Int
    ) {
        spannable.setSpan(RelativeSizeSpan(scale), start, end, flag)
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
    }

    companion object {
        private const val CODE_BG_COLOR = 0x1A000000.toInt()
        private const val QUOTE_BAR_COLOR = 0xFF999999.toInt()
        private const val QUOTE_TEXT_COLOR = 0xFF888888.toInt()
        private const val BULLET_INDENT = 48
    }
}
