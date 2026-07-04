package org.skynetsoftware.skeletonnotes.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanType

class MarkdownParserTest {

    private val parser = MarkdownParser()

    @Test
    fun `empty text returns no spans`() {
        val spans = parser.parse("")
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `plain text returns no spans`() {
        val spans = parser.parse("hello world")
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `bold single word`() {
        val spans = parser.parse("**bold**")
        assertEquals(1, spans.size)
        assertEquals(SpanType.BOLD, spans[0].type)
        assertEquals(0, spans[0].start)
        assertEquals(8, spans[0].end)
    }

    @Test
    fun `bold with text around it`() {
        val spans = parser.parse("a **bold** b")
        val boldSpans = spans.filter { it.type == SpanType.BOLD }
        assertEquals(1, boldSpans.size)
    }

    @Test
    fun `italic single word`() {
        val spans = parser.parse("*italic*")
        val italicSpans = spans.filter { it.type == SpanType.ITALIC }
        assertEquals(1, italicSpans.size)
    }

    @Test
    fun `italic does not match bold markers`() {
        val spans = parser.parse("**bold**")
        val italicSpans = spans.filter { it.type == SpanType.ITALIC }
        assertEquals(0, italicSpans.size)
    }

    @Test
    fun `strikethrough`() {
        val spans = parser.parse("~~strike~~")
        val s = spans.filter { it.type == SpanType.STRIKETHROUGH }
        assertEquals(1, s.size)
    }

    @Test
    fun `inline code`() {
        val spans = parser.parse("`code`")
        val codeSpans = spans.filter { it.type == SpanType.CODE }
        assertEquals(1, codeSpans.size)
    }

    @Test
    fun `heading level 1`() {
        val spans = parser.parse("# Heading 1")
        val headings = spans.filter { it.type == SpanType.HEADING_1 }
        assertEquals(1, headings.size)
    }

    @Test
    fun `heading level 2`() {
        val spans = parser.parse("## Heading 2")
        val headings = spans.filter { it.type == SpanType.HEADING_2 }
        assertEquals(1, headings.size)
    }

    @Test
    fun `heading level 6`() {
        val spans = parser.parse("###### Heading 6")
        val headings = spans.filter { it.type == SpanType.HEADING_6 }
        assertEquals(1, headings.size)
    }

    @Test
    fun `text starting with hash but not heading is not matched`() {
        val spans = parser.parse("#notheading")
        val headings = spans.filter { it.type == SpanType.HEADING_1 }
        assertEquals(0, headings.size)
    }

    @Test
    fun `blockquote`() {
        val spans = parser.parse("> quoted text")
        val quotes = spans.filter { it.type == SpanType.BLOCKQUOTE }
        assertEquals(1, quotes.size)
    }

    @Test
    fun `bullet list with dash`() {
        val spans = parser.parse("- item")
        val lists = spans.filter { it.type == SpanType.BULLET_LIST }
        assertEquals(1, lists.size)
    }

    @Test
    fun `bullet list with asterisk`() {
        val spans = parser.parse("* item")
        val lists = spans.filter { it.type == SpanType.BULLET_LIST }
        assertEquals(1, lists.size)
    }

    @Test
    fun `ordered list`() {
        val spans = parser.parse("1. item")
        val lists = spans.filter { it.type == SpanType.ORDERED_LIST }
        assertEquals(1, lists.size)
    }

    @Test
    fun `multiple bold on one line`() {
        val spans = parser.parse("**a** and **b**")
        val boldSpans = spans.filter { it.type == SpanType.BOLD }
        assertEquals(2, boldSpans.size)
    }

    @Test
    fun `bold italic and code on one line`() {
        val spans = parser.parse("**bold** *italic* `code` ~~strike~~")
        assertEquals(4, spans.size)
        val types = spans.map { it.type }.toSet()
        assertTrue(types.contains(SpanType.BOLD))
        assertTrue(types.contains(SpanType.ITALIC))
        assertTrue(types.contains(SpanType.CODE))
        assertTrue(types.contains(SpanType.STRIKETHROUGH))
    }

    @Test
    fun `bold takes precedence over italic when overlapping`() {
        val spans = parser.parse("**bold and *italic***")
        val boldSpans = spans.filter { it.type == SpanType.BOLD }
        val italicSpans = spans.filter { it.type == SpanType.ITALIC }
        assertEquals(1, boldSpans.size)
        assertEquals(0, italicSpans.size)
    }

    @Test
    fun `multiline with heading and paragraph`() {
        val spans = parser.parse("# Title\n**bold** text")
        val h1 = spans.filter { it.type == SpanType.HEADING_1 }
        val bold = spans.filter { it.type == SpanType.BOLD }
        assertEquals(1, h1.size)
        assertEquals(1, bold.size)
    }

    @Test
    fun `multiline with list and blockquote`() {
        val text = "- item 1\n- item 2\n\n> quote\n> more quote"
        val spans = parser.parse(text)
        val bullets = spans.filter { it.type == SpanType.BULLET_LIST }
        val quotes = spans.filter { it.type == SpanType.BLOCKQUOTE }
        assertEquals(2, bullets.size)
        assertEquals(2, quotes.size)
    }

    @Test
    fun `headings are not matched mid-line`() {
        val spans = parser.parse("text ## not a heading")
        val headings = spans.filter {
            it.type.ordinal in SpanType.HEADING_1.ordinal..SpanType.HEADING_6.ordinal
        }
        assertEquals(0, headings.size)
    }
}
