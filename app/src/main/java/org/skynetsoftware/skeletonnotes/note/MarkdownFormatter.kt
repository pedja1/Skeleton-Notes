package org.skynetsoftware.skeletonnotes.note

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

/**
 * Serializes note content to Markdown and parses it back symmetrically - the Markdown counterpart
 * of the editor's WYSIWYG span model.
 *
 * The editor represents formatting with the same spans it always has: bold/italic as
 * [StyleSpan], strikethrough/underline as [StrikethroughSpan]/[UnderlineSpan], links as
 * [URLSpan], H1/H2 as a [RelativeSizeSpan] plus a bold [StyleSpan] over the whole paragraph,
 * and checklist items as a [ChecklistSpan]. [toMarkdown] walks the content paragraph-by-paragraph and emits `# `/`## `
 * heading prefixes and `- [ ] `/`- [x] ` checklist prefixes, plus `**`/`*`/`~~` and `<u></u>` for
 * inline formatting; [fromMarkdown] is the deliberate inverse, parsing each `\n`-separated line back
 * into those spans and joining the lines with a single `\n` (mirroring the single-newline paragraph
 * invariant the old HtmlFormatter maintained, so the load/save round trip stays stable).
 *
 * Markdown has no native underline, so it is serialized as the inline HTML `<u>…</u>`, consistent
 * with the app's legacy-HTML lineage; every other token is standard (GFM) Markdown.
 */
@Suppress("TooManyFunctions") // A cohesive serializer; its small parse/serialize helpers belong together.
object MarkdownFormatter {
    /** Relative size for an H1, matching Android's internal `HEADING_SIZES[0]`. */
    const val H1_SCALE = 1.5f

    /** Relative size for an H2, matching Android's internal `HEADING_SIZES[1]`. */
    const val H2_SCALE = 1.4f

    private const val SCALE_TOLERANCE = 0.05f

    /** Matches the leading `#`..`######` of a heading line (before any inline parsing/unescaping). */
    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Matches a GFM task-list line `- [ ] …` / `- [x] …`, capturing the check state and content. */
    private val CHECKLIST_REGEX = Regex("^- \\[([ xX])\\] ?(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Matches an inline link `[text](url)`, capturing the display text and destination URL. */
    private val LINK_REGEX = Regex("\\[([^]]*)]\\(([^)]+)\\)")

    /**
     * An inline emphasis, with the marker(s) that open and close it. Symmetric Markdown emphases use
     * the same string for both; underline uses distinct HTML tags.
     */
    private enum class Emphasis(
        val open: String,
        val close: String,
    ) {
        BOLD("**", "**"),
        ITALIC("*", "*"),
        STRIKETHROUGH("~~", "~~"),
        UNDERLINE("<u>", "</u>"),
    }

    /** Canonical nesting order: outer-most first, so markers open and close in a validly nested way. */
    private val CANONICAL_ORDER = listOf(Emphasis.BOLD, Emphasis.ITALIC, Emphasis.STRIKETHROUGH, Emphasis.UNDERLINE)

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
        val checklists = mutableListOf<ChecklistRange>()
        source.split('\n').forEachIndexed { index, line ->
            if (index > 0) builder.append('\n')

            val checklist = CHECKLIST_REGEX.find(line)
            val heading = if (checklist == null) HEADING_REGEX.find(line) else null
            val level = heading?.groupValues?.get(1)?.length ?: 0
            val content =
                when {
                    checklist != null -> checklist.groupValues[2]
                    level > 0 -> heading!!.groupValues[2]
                    else -> line
                }

            val start = builder.length
            parseInline(content, builder)
            val end = builder.length

            if (checklist != null) {
                checklists.add(ChecklistRange(start, end, checklist.groupValues[1].lowercase() == "x"))
            } else if (level > 0 && end > start) {
                headings.add(HeadingRange(start, end, level))
            }
        }

        // Heading and checklist spans are applied only after the whole document is built. Applying
        // them inside the loop would let the end mark grow across the '\n' and swallow every paragraph
        // appended afterwards. Heading spans keep SPAN_EXCLUSIVE_INCLUSIVE so the editor's applyHeading
        // path (which also uses it, so typing at a heading's end keeps extending it) stays consistent.
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
        for (checklistRange in checklists) {
            val spanEnd = paragraphSpanEnd(builder, checklistRange.end)
            // An empty trailing item is a zero-length span and must stay the non-rendering base
            // class — see [ChecklistItemSpan] for the layout reason.
            val span =
                if (spanEnd > checklistRange.start) {
                    ChecklistSpan(checklistRange.checked)
                } else {
                    ChecklistItemSpan(checklistRange.checked)
                }
            builder.setSpan(span, checklistRange.start, spanEnd, Spannable.SPAN_PARAGRAPH)
        }
        return builder
    }

    /** A parsed heading's text range and level, collected during parsing and applied afterwards. */
    private data class HeadingRange(
        val start: Int,
        val end: Int,
        val level: Int,
    )

    /** A parsed checklist item's text range and checked state, applied after the document is built. */
    private data class ChecklistRange(
        val start: Int,
        val end: Int,
        val checked: Boolean,
    )

    /** Paragraph spans (SPAN_PARAGRAPH) must end at a '\n' boundary or the buffer end. */
    private fun paragraphSpanEnd(
        text: CharSequence,
        end: Int,
    ): Int = if (end < text.length && text[end] == '\n') end + 1 else end

    private fun serializeParagraph(
        spanned: Spanned,
        start: Int,
        end: Int,
    ): String {
        val checked = checklistCheckedFor(spanned, start, end)
        if (checked != null) {
            val prefix = if (checked) "- [x] " else "- [ ] "
            return if (start == end) prefix else prefix + serializeInline(spanned, start, end, isHeading = false)
        }
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
        // Checklist look-alikes are already protected because '[' and ']' are escaped by escapeInline.
        val escaped = if (level == 0 && inline.startsWith("#")) "\\$inline" else inline
        return prefix + escaped
    }

    private fun checklistCheckedFor(
        spanned: Spanned,
        start: Int,
        end: Int,
    ): Boolean? {
        val queryEnd = if (end > start) end else (start + 1).coerceAtMost(spanned.length)
        // Only a span that starts in this paragraph counts; the previous paragraph's SPAN_PARAGRAPH
        // can be returned at the boundary, which would wrongly mark an empty next line as a checklist.
        return spanned
            .getSpans(start, queryEnd, ChecklistItemSpan::class.java)
            .firstOrNull { spanned.getSpanStart(it) >= start }
            ?.checked
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
     * of active emphases on a stack so overlapping runs close and reopen in a validly nested way.
     * When [isHeading] the whole-paragraph bold conveyed by the `#` prefix is skipped so no redundant
     * `**` is emitted inside the heading.
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
        while (open.isNotEmpty()) sb.append(open.removeLast().close)
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
            if (spanned.isComposing(span)) continue
            when (span.style) {
                Typeface.BOLD -> if (!isHeading) result.add(Emphasis.BOLD)
                Typeface.ITALIC -> result.add(Emphasis.ITALIC)
                Typeface.BOLD_ITALIC -> {
                    if (!isHeading) result.add(Emphasis.BOLD)
                    result.add(Emphasis.ITALIC)
                }
            }
        }
        if (spanned.getSpans(from, to, StrikethroughSpan::class.java).any { !spanned.isComposing(it) }) {
            result.add(Emphasis.STRIKETHROUGH)
        }
        if (spanned.getSpans(from, to, UnderlineSpan::class.java).any { !spanned.isComposing(it) }) {
            result.add(Emphasis.UNDERLINE)
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
            while (open.size > divergence) sb.append(open.removeLast().close)
        }
        for (emphasis in CANONICAL_ORDER) {
            if (emphasis in active && emphasis !in open) {
                sb.append(emphasis.open)
                open.addLast(emphasis)
            }
        }
    }

    private fun escapeInline(
        spanned: Spanned,
        from: Int,
        to: Int,
    ): String {
        val urlSpan =
            spanned
                .getSpans(from, to, URLSpan::class.java)
                .firstOrNull { spanned.getSpanStart(it) <= from && spanned.getSpanEnd(it) >= to }
        if (urlSpan != null) {
            if (urlSpan is AutoDetectedUrlSpan) return spanned.subSequence(from, to).toString()
            return "[${spanned.subSequence(from, to)}](${urlSpan.url})"
        }
        val sb = StringBuilder(to - from)
        for (index in from until to) {
            val c = spanned[index]
            if (c == '\\' || c == '*' || c == '[' || c == ']' || c == '~' || c == '<') sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    private fun parseInline(
        text: String,
        builder: SpannableStringBuilder,
    ) {
        val openAt = HashMap<Emphasis, Int>()
        var i = 0
        while (i < text.length) {
            i =
                when {
                    isEscape(text, i) -> appendEscaped(text, i, builder)
                    isLink(text, i) -> appendLink(text, i, builder)
                    text.startsWith(
                        Emphasis.UNDERLINE.close,
                        i,
                    ) -> closeEmphasis(Emphasis.UNDERLINE, openAt, builder, i)
                    text.startsWith(Emphasis.UNDERLINE.open, i) -> openEmphasis(Emphasis.UNDERLINE, openAt, builder, i)
                    isBold(text, i) -> toggleEmphasis(Emphasis.BOLD, openAt, builder, i)
                    text.startsWith(Emphasis.STRIKETHROUGH.open, i) ->
                        toggleEmphasis(Emphasis.STRIKETHROUGH, openAt, builder, i)
                    text[i] == '*' -> toggleEmphasis(Emphasis.ITALIC, openAt, builder, i)
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

    /** True when [index] begins a link (`[text](url)`). */
    private fun isLink(
        text: String,
        index: Int,
    ) = text[index] == '[' && LINK_REGEX.find(text, index) != null

    /** Appends the display text of a link at [index] decorated by a [URLSpan] and returns the next index. */
    private fun appendLink(
        text: String,
        index: Int,
        builder: SpannableStringBuilder,
    ): Int {
        val match = LINK_REGEX.find(text, index) ?: return appendLiteral(text, index, builder)
        val displayText = match.groupValues[1]
        val url = match.groupValues[2]
        val start = builder.length
        builder.append(displayText)
        builder.setSpan(URLSpan(url), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return index + match.value.length
    }

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

    /** Handles a symmetric marker (`**`, `*`, `~~`), opening or closing [emphasis]. */
    private fun toggleEmphasis(
        emphasis: Emphasis,
        openAt: HashMap<Emphasis, Int>,
        builder: SpannableStringBuilder,
        index: Int,
    ): Int {
        val startedAt = openAt.remove(emphasis)
        if (startedAt == null) {
            openAt[emphasis] = builder.length
        } else {
            builder.setSpan(spanFor(emphasis), startedAt, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return index + emphasis.open.length
    }

    /** Records the open position of an asymmetric emphasis (`<u>`). */
    private fun openEmphasis(
        emphasis: Emphasis,
        openAt: HashMap<Emphasis, Int>,
        builder: SpannableStringBuilder,
        index: Int,
    ): Int {
        openAt[emphasis] = builder.length
        return index + emphasis.open.length
    }

    /** Applies an asymmetric emphasis (`</u>`) over the text accumulated since its opening marker. */
    private fun closeEmphasis(
        emphasis: Emphasis,
        openAt: HashMap<Emphasis, Int>,
        builder: SpannableStringBuilder,
        index: Int,
    ): Int {
        val startedAt = openAt.remove(emphasis)
        if (startedAt != null) {
            builder.setSpan(spanFor(emphasis), startedAt, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return index + emphasis.close.length
    }

    private fun spanFor(emphasis: Emphasis): Any =
        when (emphasis) {
            Emphasis.BOLD -> StyleSpan(Typeface.BOLD)
            Emphasis.ITALIC -> StyleSpan(Typeface.ITALIC)
            Emphasis.STRIKETHROUGH -> StrikethroughSpan()
            Emphasis.UNDERLINE -> UnderlineSpan()
        }

    /** A transient span the IME places on composing text must not be serialized as user formatting. */
    private fun Spanned.isComposing(span: Any) = (getSpanFlags(span) and Spanned.SPAN_COMPOSING) != 0

    private fun approxEquals(
        a: Float,
        b: Float,
    ) = kotlin.math.abs(a - b) < SCALE_TOLERANCE
}
