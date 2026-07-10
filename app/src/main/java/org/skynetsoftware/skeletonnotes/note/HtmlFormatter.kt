package org.skynetsoftware.skeletonnotes.note

import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Serializes note content to HTML using semantic heading tags, and parses it back symmetrically.
 *
 * The framework's [Html.toHtml] does not emit heading tags, so [toHtml] walks the content
 * paragraph-by-paragraph and maps the heading spans that the editor applies back to `<h1>`/`<h2>`;
 * all other inline formatting (`<b>`/`<i>`/`<u>`/`<img>`) is delegated to [Html.toHtml].
 *
 * [fromHtml] is the deliberate inverse: rather than calling [Html.fromHtml] on the whole document
 * (which separates block elements with *two* newlines while [toHtml] splits paragraphs on a
 * *single* newline - a mismatch that duplicates blank paragraphs on every load/save round trip),
 * it parses the document block-by-block and joins the blocks with a single `\n`. Headings do not
 * carry an explicit `<b>`: the `<hN>` wrapper conveys bold, and [fromHtml] re-applies the bold
 * span for display, so the round trip stays byte-stable.
 */
object HtmlFormatter {

    /** Relative size for an H1, matching Android's internal `HEADING_SIZES[0]`. */
    const val H1_SCALE = 1.5f

    /** Relative size for an H2, matching Android's internal `HEADING_SIZES[1]`. */
    const val H2_SCALE = 1.4f

    private const val SCALE_TOLERANCE = 0.05f

    @Suppress("DEPRECATION")
    fun toHtml(spanned: Spanned): String {
        val text = spanned.toString()
        val builder = StringBuilder()
        var paragraphStart = 0
        while (paragraphStart <= text.length) {
            var paragraphEnd = text.indexOf('\n', paragraphStart)
            if (paragraphEnd < 0) paragraphEnd = text.length

            builder.append(serializeParagraph(spanned, paragraphStart, paragraphEnd))

            if (paragraphEnd == text.length) break
            paragraphStart = paragraphEnd + 1
        }
        return builder.toString()
    }

    /**
     * Parses HTML produced by [toHtml] back into a [Spanned], as the exact inverse of [toHtml].
     * Each top-level block (`<p>`/`<h1>`..`<h6>`) becomes one `\n`-separated paragraph; heading
     * blocks get a [RelativeSizeSpan] plus a bold [StyleSpan] (matching the spans the editor
     * applies). Falls back to [Html.fromHtml] for content that carries no block wrappers.
     */
    @Suppress("DEPRECATION")
    fun fromHtml(html: String, imageGetter: Html.ImageGetter): Spanned {
        val blocks = BLOCK_REGEX.findAll(html).toList()
        if (blocks.isEmpty()) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
        }

        val builder = SpannableStringBuilder()
        blocks.forEachIndexed { index, match ->
            if (index > 0) builder.append('\n')

            val tag = match.groupValues[1].lowercase()
            val inner = match.groupValues[2]
            val start = builder.length
            builder.append(
                trimTrailingNewlines(
                    Html.fromHtml(inner, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
                )
            )
            val end = builder.length

            val scale = when (tag) {
                "h1" -> H1_SCALE
                else -> H2_SCALE // h2..h6 collapse to a single sub-heading size
            }
            if (tag != "p" && end > start) {
                builder.setSpan(RelativeSizeSpan(scale), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        }
        return builder
    }

    /**
     * Matches a single top-level block element and captures its tag and inline content. [toHtml]
     * never nests block elements, so the reluctant `.*?` plus the `\1` backreference is safe.
     */
    private val BLOCK_REGEX = Regex(
        "<(p|h[1-6])\\b[^>]*>(.*?)</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun trimTrailingNewlines(spanned: Spanned): CharSequence {
        var end = spanned.length
        while (end > 0 && spanned[end - 1] == '\n') end--
        return if (end == spanned.length) spanned else spanned.subSequence(0, end)
    }

    @Suppress("DEPRECATION")
    private fun serializeParagraph(spanned: Spanned, start: Int, end: Int): String {
        val tag = headingTagFor(spanned, start, end)

        if (start == end) {
            return "<$tag dir=\"ltr\"><br></$tag>\n"
        }

        // Copy the paragraph substring and strip the heading spans so Html.toHtml does not emit
        // a (buggy) inline font-size for them - the <hN> wrapper carries the sizing instead.
        val paragraph = SpannableStringBuilder(spanned.subSequence(start, end))
        paragraph.getSpans(0, paragraph.length, RelativeSizeSpan::class.java)
            .forEach { paragraph.removeSpan(it) }

        // A heading is also bold, but the <hN> wrapper already conveys that (Html.fromHtml re-adds
        // a bold span for it). Emitting <b> as well would accumulate nested <b> on every round trip,
        // so drop the bold spans here - re-saving a corrupted heading self-heals it.
        if (tag != "p") {
            paragraph.getSpans(0, paragraph.length, StyleSpan::class.java)
                .filter { it.style == Typeface.BOLD }
                .forEach { paragraph.removeSpan(it) }
        }

        val inline = stripParagraphWrapper(Html.toHtml(paragraph))
        return "<$tag dir=\"ltr\">$inline</$tag>\n"
    }

    private fun headingTagFor(spanned: Spanned, start: Int, end: Int): String {
        val sizeSpan = spanned.getSpans(start, end, RelativeSizeSpan::class.java)
            .firstOrNull() ?: return "p"
        return when {
            approxEquals(sizeSpan.sizeChange, H1_SCALE) -> "h1"
            approxEquals(sizeSpan.sizeChange, H2_SCALE) -> "h2"
            else -> "p"
        }
    }

    private fun approxEquals(a: Float, b: Float) = kotlin.math.abs(a - b) < SCALE_TOLERANCE

    /**
     * Html.toHtml wraps content in `<p dir="ltr">...</p>\n` (or `<div>` etc). Remove the single
     * outer block wrapper so the inline formatting can be re-wrapped in the target heading tag.
     */
    private fun stripParagraphWrapper(html: String): String {
        val trimmed = html.trim()
        val open = Regex("^<(p|div|h[1-6])[^>]*>", RegexOption.IGNORE_CASE).find(trimmed)
            ?: return trimmed
        val close = Regex("</(p|div|h[1-6])>$", RegexOption.IGNORE_CASE).find(trimmed)
            ?: return trimmed
        return trimmed.substring(open.range.last + 1, close.range.first)
    }
}
