package org.skynetsoftware.skeletonnotes.note

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import org.skynetsoftware.skeletonnotes.note.MarkdownFormatter.CANONICAL_ORDER
import org.skynetsoftware.skeletonnotes.note.MarkdownFormatter.fromMarkdown
import org.skynetsoftware.skeletonnotes.note.MarkdownFormatter.toMarkdown

/**
 * Serializes note content to Markdown and parses it back symmetrically - the Markdown counterpart
 * of the editor's WYSIWYG span model.
 *
 * The editor represents formatting with the same spans it always has: bold/italic as
 * [StyleSpan], H1/H2 as a [RelativeSizeSpan] plus a bold [StyleSpan] over the whole paragraph.
 * [toMarkdown] walks the content paragraph-by-paragraph and emits
 * `# `/`## ` prefixes for headings and `**`/`*`/`![]()` for inline formatting; [fromMarkdown] is
 * the deliberate inverse, parsing each `\n`-separated line back into those spans and joining the
 * lines with a single `\n` (mirroring the single-newline paragraph invariant the old HtmlFormatter
 * maintained, so the load/save round trip stays stable).
 */
object MarkdownFormatter {
    /** Relative size for an H1, matching Android's internal `HEADING_SIZES[0]`. */
    const val H1_SCALE = 1.5f

    /** Relative size for an H2, matching Android's internal `HEADING_SIZES[1]`. */
    const val H2_SCALE = 1.4f

    private const val SCALE_TOLERANCE = 0.05f

    /** Matches the leading `#`..`######` of a heading line (before any inline parsing/unescaping). */
    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$", RegexOption.DOT_MATCHES_ALL)

    private enum class Emphasis(
        val marker: String,
    ) {
        BOLD("**"),
        ITALIC("*"),
    }

    /** Canonical nesting order: bold wraps italic, so `**` opens before `*` and closes after it. */
    private val CANONICAL_ORDER = listOf(Emphasis.BOLD, Emphasis.ITALIC)

    fun toMarkdown(spanned: Spanned): String {
        val text = spanned.toString()
        val builder = StringBuilder()
        var paragraphStart = 0
        var first = true
        while (paragraphStart <= text.length) {
            var paragraphEnd = text.indexOf('\n', paragraphStart)
            if (paragraphEnd < 0) paragraphEnd = text.length

            if (!first) builder.append('\n')
            first = false
            builder.append(serializeParagraph(spanned, paragraphStart, paragraphEnd))

            if (paragraphEnd == text.length) break
            paragraphStart = paragraphEnd + 1
        }
        return builder.toString()
    }

    /**
     * Parses Markdown produced by [toMarkdown] back into a [Spanned], as the exact inverse of
     * [toMarkdown]. Legacy HTML content is converted to Markdown first (see class docs).
     */
    fun fromMarkdown(source: String): Spanned {
        val builder = SpannableStringBuilder()
        val headings = mutableListOf<HeadingRange>()
        source.split('\n').forEachIndexed { index, line ->
            if (index > 0) builder.append('\n')

            val heading = HEADING_REGEX.find(line)
            val level = heading?.groupValues?.get(1)?.length ?: 0
            val content = if (level > 0) heading!!.groupValues[2] else line

            val start = builder.length
            parseInline(content, builder)
            val end = builder.length

            if (level > 0 && end > start) {
                headings.add(HeadingRange(start, end, level))
            }
        }

        // Heading spans are applied only after the whole document is built. Applying them inside the
        // loop would let the SPAN_EXCLUSIVE_INCLUSIVE end mark grow across the '\n' and swallow every
        // paragraph appended afterwards. The INCLUSIVE flag is kept so the editor's applyHeading path
        // (which also uses it, so typing at a heading's end keeps extending it) stays consistent.
        for (headingRange in headings) {
            val scale = if (headingRange.level == 1) H1_SCALE else H2_SCALE // h2..h6 -> sub-heading
            builder.setSpan(
                RelativeSizeSpan(scale),
                headingRange.start,
                headingRange.end,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE,
            )
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                headingRange.start,
                headingRange.end,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE,
            )
        }
        return builder
    }

    /** A parsed heading's text range and level, collected during parsing and applied afterwards. */
    private data class HeadingRange(
        val start: Int,
        val end: Int,
        val level: Int,
    )

    private fun serializeParagraph(
        spanned: Spanned,
        start: Int,
        end: Int,
    ): String {
        val level = headingLevelFor(spanned, start, end)
        val prefix =
            when (level) {
                1 -> "# "
                2 -> "## "
                else -> ""
            }
        if (start == end) return prefix
        val inline = serializeInline(spanned, start, end, isHeading = level != 0)
        // A plain paragraph that happens to start with '#' would be misread as a heading on load.
        val escaped = if (level == 0 && inline.startsWith("#")) "\\$inline" else inline
        return prefix + escaped
    }

    private fun headingLevelFor(
        spanned: Spanned,
        start: Int,
        end: Int,
    ): Int {
        val sizeSpan =
            spanned
                .getSpans(start, end, RelativeSizeSpan::class.java)
                .firstOrNull() ?: return 0
        return when {
            approxEquals(sizeSpan.sizeChange, H1_SCALE) -> 1
            approxEquals(sizeSpan.sizeChange, H2_SCALE) -> 2
            else -> 0
        }
    }

    /**
     * Serializes a single paragraph's inline formatting. Walks span transitions, tracking the set
     * of active emphases on a stack so overlapping bold/italic runs close and reopen in a validly
     * nested way. When [isHeading] the whole-paragraph bold conveyed by the `#` prefix is skipped so
     * no redundant `**` is emitted inside the heading.
     */
    private fun serializeInline(
        spanned: Spanned,
        start: Int,
        end: Int,
        isHeading: Boolean,
    ): String {
        val sb = StringBuilder()
        val open = ArrayDeque<Emphasis>()
        var i = start
        while (i < end) {
            val next = spanned.nextSpanTransition(i, end, Any::class.java)

            val active = activeEmphasis(spanned, i, next, isHeading)
            adjustStack(sb, open, active)

            sb.append(escapeInline(spanned, i, next))

            i = next
        }
        while (open.isNotEmpty()) sb.append(open.removeLast().marker)
        return sb.toString()
    }

    private fun activeEmphasis(
        spanned: Spanned,
        from: Int,
        to: Int,
        isHeading: Boolean,
    ): Set<Emphasis> {
        val result = mutableSetOf<Emphasis>()
        for (span in spanned.getSpans(from, to, StyleSpan::class.java)) {
            when (span.style) {
                Typeface.BOLD -> if (!isHeading) result.add(Emphasis.BOLD)
                Typeface.ITALIC -> result.add(Emphasis.ITALIC)
                Typeface.BOLD_ITALIC -> {
                    if (!isHeading) result.add(Emphasis.BOLD)
                    result.add(Emphasis.ITALIC)
                }
            }
        }
        return result
    }

    /**
     * Closes emphasis markers that are no longer active (and everything stacked above them), then
     * opens the newly active ones in [CANONICAL_ORDER], keeping the emitted markers validly nested.
     */
    private fun adjustStack(
        sb: StringBuilder,
        open: ArrayDeque<Emphasis>,
        active: Set<Emphasis>,
    ) {
        val divergence = open.indexOfFirst { it !in active }
        if (divergence >= 0) {
            while (open.size > divergence) sb.append(open.removeLast().marker)
        }
        for (emphasis in CANONICAL_ORDER) {
            if (emphasis in active && emphasis !in open) {
                sb.append(emphasis.marker)
                open.addLast(emphasis)
            }
        }
    }

    private fun escapeInline(
        spanned: Spanned,
        from: Int,
        to: Int,
    ): String {
        val sb = StringBuilder(to - from)
        for (index in from until to) {
            val c = spanned[index]
            if (c == '\\' || c == '*' || c == '[' || c == ']') sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    private fun parseInline(
        text: String,
        builder: SpannableStringBuilder,
    ) {
        val emphasis = EmphasisTracker()
        var i = 0
        while (i < text.length) {
            i =
                when {
                    isEscape(text, i) -> appendEscaped(text, i, builder)
                    isBold(text, i) -> emphasis.toggleBold(builder, i)
                    text[i] == '*' -> emphasis.toggleItalic(builder, i)
                    else -> appendLiteral(text, i, builder)
                }
        }
    }

    /** True when [index] begins a backslash escape sequence (`\` followed by another char). */
    private fun isEscape(
        text: String,
        index: Int,
    ) = text[index] == '\\' && index + 1 < text.length

    /** True when [index] begins a bold marker (`**`). */
    private fun isBold(
        text: String,
        index: Int,
    ) = text[index] == '*' && index + 1 < text.length && text[index + 1] == '*'

    /** Appends the character escaped by the backslash at [index] and returns the next index. */
    private fun appendEscaped(
        text: String,
        index: Int,
        builder: SpannableStringBuilder,
    ): Int {
        builder.append(text[index + 1])
        return index + 2
    }

    /** Appends the single literal character at [index] and returns the next index. */
    private fun appendLiteral(
        text: String,
        index: Int,
        builder: SpannableStringBuilder,
    ): Int {
        builder.append(text[index])
        return index + 1
    }

    /**
     * Tracks the open positions of the bold/italic runs while parsing so a closing marker can apply
     * the matching [StyleSpan] over the text accumulated since the opening marker.
     */
    private class EmphasisTracker {
        private var boldStart = -1
        private var italicStart = -1

        /** Handles a `**` marker at [index], opening or closing a bold run. Returns the next index. */
        fun toggleBold(
            builder: SpannableStringBuilder,
            index: Int,
        ): Int {
            if (boldStart < 0) {
                boldStart = builder.length
            } else {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    boldStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                boldStart = -1
            }
            return index + 2
        }

        /** Handles a `*` marker at [index], opening or closing an italic run. Returns the next index. */
        fun toggleItalic(
            builder: SpannableStringBuilder,
            index: Int,
        ): Int {
            if (italicStart < 0) {
                italicStart = builder.length
            } else {
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    italicStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                italicStart = -1
            }
            return index + 1
        }
    }

    private fun approxEquals(
        a: Float,
        b: Float,
    ) = kotlin.math.abs(a - b) < SCALE_TOLERANCE
}
