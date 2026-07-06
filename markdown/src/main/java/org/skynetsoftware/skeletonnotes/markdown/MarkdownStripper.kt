package org.skynetsoftware.skeletonnotes.markdown

import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanRange
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanType

/**
 * Strips markdown syntax delimiters from raw text and adjusts span positions accordingly.
 *
 * For a given raw markdown text and its parsed spans, returns the clean
 * display text with all syntax characters removed, along with adjusted
 * [SpanRange] positions pointing into the clean text.
 */
object MarkdownStripper {

    fun strip(text: String, spans: List<SpanRange>): Pair<String, List<SpanRange>> {
        val skip = BooleanArray(text.length)

        for (span in spans) {
            markSyntax(text, span, skip)
        }

        val adjusted = IntArray(text.length + 1)
        val cleanText = StringBuilder()
        for (i in text.indices) {
            adjusted[i + 1] = adjusted[i] + if (skip[i]) 0 else 1
            if (!skip[i]) {
                cleanText.append(text[i])
            }
        }

        val cleanPos = cleanText.length
        val adjustedSpans = spans.map { span ->
            val newStart = adjusted[span.start.coerceAtMost(text.length)]
            val newEnd = adjusted[span.end.coerceAtMost(text.length)]
            SpanRange(newStart, newEnd, span.type)
        }

        return cleanText.toString() to adjustedSpans
    }

    private fun markSyntax(text: String, span: SpanRange, skip: BooleanArray) {
        when (span.type) {
            SpanType.HEADING_1, SpanType.HEADING_2, SpanType.HEADING_3,
            SpanType.HEADING_4, SpanType.HEADING_5, SpanType.HEADING_6 ->
                markHeadingSyntax(text, span.start, skip)
            SpanType.BOLD -> markInlineWrap(text, span.start, span.end, "**", skip)
            SpanType.ITALIC -> markInlineWrap(text, span.start, span.end, "*", skip)
            SpanType.STRIKETHROUGH -> markInlineWrap(text, span.start, span.end, "~~", skip)
            SpanType.CODE -> markInlineWrap(text, span.start, span.end, "`", skip)
            SpanType.BLOCKQUOTE -> markBlockquoteSyntax(text, span.start, skip)
            SpanType.BULLET_LIST -> markBulletListSyntax(text, span.start, skip)
            SpanType.ORDERED_LIST -> markOrderedListSyntax(text, span.start, skip)
        }
    }

    private fun markHeadingSyntax(text: String, lineStart: Int, skip: BooleanArray) {
        var i = lineStart
        while (i < text.length && text[i] == '#') {
            skip[i] = true
            i++
        }
        while (i < text.length && text[i].isWhitespace()) {
            skip[i] = true
            i++
        }
    }

    private fun markBlockquoteSyntax(text: String, lineStart: Int, skip: BooleanArray) {
        if (lineStart < text.length && text[lineStart] == '>') {
            skip[lineStart] = true
            var i = lineStart + 1
            while (i < text.length && text[i].isWhitespace()) {
                skip[i] = true
                i++
            }
        }
    }

    private fun markBulletListSyntax(text: String, lineStart: Int, skip: BooleanArray) {
        if (lineStart < text.length &&
            (text[lineStart] == '-' || text[lineStart] == '*' || text[lineStart] == '+')
        ) {
            skip[lineStart] = true
            var i = lineStart + 1
            while (i < text.length && text[i].isWhitespace()) {
                skip[i] = true
                i++
            }
        }
    }

    private fun markOrderedListSyntax(text: String, lineStart: Int, skip: BooleanArray) {
        var i = lineStart
        while (i < text.length && text[i].isDigit()) {
            skip[i] = true
            i++
        }
        if (i < text.length && text[i] == '.') {
            skip[i] = true
            i++
        }
        while (i < text.length && text[i].isWhitespace()) {
            skip[i] = true
            i++
        }
    }

    private fun markInlineWrap(
        text: String,
        spanStart: Int,
        spanEnd: Int,
        delimiter: String,
        skip: BooleanArray
    ) {
        for (j in delimiter.indices) {
            val idx = spanStart + j
            if (idx < text.length) skip[idx] = true
        }
        for (j in delimiter.indices) {
            val idx = spanEnd - delimiter.length + j
            if (idx >= 0 && idx < text.length) skip[idx] = true
        }
    }
}
