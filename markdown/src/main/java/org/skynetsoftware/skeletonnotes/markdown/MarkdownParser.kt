package org.skynetsoftware.skeletonnotes.markdown

class MarkdownParser {

    data class SpanRange(
        val start: Int,
        val end: Int,
        val type: SpanType
    )

    enum class SpanType {
        HEADING_1, HEADING_2, HEADING_3, HEADING_4, HEADING_5, HEADING_6,
        BOLD, ITALIC, STRIKETHROUGH, CODE,
        BULLET_LIST, ORDERED_LIST, BLOCKQUOTE
    }

    fun parse(text: CharSequence): List<SpanRange> {
        val results = mutableListOf<SpanRange>()
        val lines = text.split("\n")
        var lineStart = 0

        for (line in lines) {
            val lineLength = line.length
            if (lineLength == 0) {
                lineStart += 1
                continue
            }

            parseLineLevel(line, lineStart, results)
            parseInline(line, lineStart, results)

            lineStart += lineLength + 1
        }

        return results
    }

    private fun parseLineLevel(
        line: String,
        lineOffset: Int,
        results: MutableList<SpanRange>
    ) {
        val headingMatch = HEADING_REGEX.find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val type = when (level) {
                1 -> SpanType.HEADING_1
                2 -> SpanType.HEADING_2
                3 -> SpanType.HEADING_3
                4 -> SpanType.HEADING_4
                5 -> SpanType.HEADING_5
                6 -> SpanType.HEADING_6
                else -> return
            }
            results.add(SpanRange(lineOffset, lineOffset + line.length, type))
            return
        }

        val blockquoteMatch = BLOCKQUOTE_REGEX.find(line)
        if (blockquoteMatch != null) {
            results.add(SpanRange(lineOffset, lineOffset + line.length, SpanType.BLOCKQUOTE))
            return
        }

        val unorderedMatch = UNORDERED_LIST_REGEX.find(line)
        if (unorderedMatch != null) {
            results.add(SpanRange(lineOffset, lineOffset + line.length, SpanType.BULLET_LIST))
            return
        }

        val orderedMatch = ORDERED_LIST_REGEX.find(line)
        if (orderedMatch != null) {
            results.add(SpanRange(lineOffset, lineOffset + line.length, SpanType.ORDERED_LIST))
            return
        }
    }

    private fun parseInline(
        text: CharSequence,
        offset: Int,
        results: MutableList<SpanRange>
    ) {
        val inlineResults = mutableListOf<SpanRange>()

        addInlineMatches(text, offset, inlineResults, BOLD_REGEX, SpanType.BOLD)
        addInlineMatches(text, offset, inlineResults, STRIKETHROUGH_REGEX, SpanType.STRIKETHROUGH)
        addInlineMatches(text, offset, inlineResults, CODE_REGEX, SpanType.CODE)
        addInlineMatches(text, offset, inlineResults, ITALIC_REGEX, SpanType.ITALIC)

        results.addAll(inlineResults)
    }

    private fun addInlineMatches(
        text: CharSequence,
        offset: Int,
        results: MutableList<SpanRange>,
        regex: Regex,
        type: SpanType
    ) {
        val covered = results.map { it.start to it.end }

        for (match in regex.findAll(text)) {
            val mStart = offset + match.range.first
            val mEnd = offset + match.range.last + 1

            val overlaps = covered.any { (cStart, cEnd) ->
                mStart < cEnd && mEnd > cStart
            }

            if (!overlaps) {
                results.add(SpanRange(mStart, mEnd, type))
            }
        }
    }

    companion object {
        private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
        private val BLOCKQUOTE_REGEX = Regex("^>\\s*(.*)$")
        private val UNORDERED_LIST_REGEX = Regex("^[\\-\\*\\+]\\s+(.+)$")
        private val ORDERED_LIST_REGEX = Regex("^\\d+\\.\\s+(.+)$")
        private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
        private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
        private val STRIKETHROUGH_REGEX = Regex("~~(.+?)~~")
        private val CODE_REGEX = Regex("`(.+?)`")
    }
}
