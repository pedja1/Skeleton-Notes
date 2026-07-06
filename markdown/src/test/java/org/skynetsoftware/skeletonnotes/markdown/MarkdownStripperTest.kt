package org.skynetsoftware.skeletonnotes.markdown

import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanRange
import org.skynetsoftware.skeletonnotes.markdown.MarkdownParser.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStripperTest {

    private val parser = MarkdownParser()

    @Test
    fun stripsHeadingSyntax() {
        val result = strip("# Title")

        assertEquals("Title", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.HEADING_1, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(5, result.second[0].end)
    }

    @Test
    fun stripsHeadingLevel2Syntax() {
        val result = strip("## Subtitle")

        assertEquals("Subtitle", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.HEADING_2, result.second[0].type)
    }

    @Test
    fun stripsBoldSyntax() {
        val result = strip("**bold**")

        assertEquals("bold", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.BOLD, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(4, result.second[0].end)
    }

    @Test
    fun stripsItalicSyntax() {
        val result = strip("*italic*")

        assertEquals("italic", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.ITALIC, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(6, result.second[0].end)
    }

    @Test
    fun stripsStrikethroughSyntax() {
        val result = strip("~~strike~~")

        assertEquals("strike", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.STRIKETHROUGH, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(6, result.second[0].end)
    }

    @Test
    fun stripsCodeSyntax() {
        val result = strip("`code`")

        assertEquals("code", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.CODE, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(4, result.second[0].end)
    }

    @Test
    fun stripsBlockquoteSyntax() {
        val result = strip("> quoted text")

        assertEquals("quoted text", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.BLOCKQUOTE, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(11, result.second[0].end)
    }

    @Test
    fun stripsBulletListDashSyntax() {
        val result = strip("- item one")

        assertEquals("item one", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.BULLET_LIST, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(8, result.second[0].end)
    }

    @Test
    fun stripsBulletListAsteriskSyntax() {
        val result = strip("* item two")

        assertEquals("item two", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.BULLET_LIST, result.second[0].type)
    }

    @Test
    fun stripsOrderedListSyntax() {
        val result = strip("1. first item")

        assertEquals("first item", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.ORDERED_LIST, result.second[0].type)
        assertEquals(0, result.second[0].start)
        assertEquals(10, result.second[0].end)
    }

    @Test
    fun stripsMultiDigitOrderedListSyntax() {
        val result = strip("10. item ten")

        assertEquals("item ten", result.first)
        assertEquals(1, result.second.size)
        assertEquals(SpanType.ORDERED_LIST, result.second[0].type)
    }

    @Test
    fun stripsHeadingWithBoldAndItalic() {
        val result = strip("# **bold** and *italic*")

        assertEquals("bold and italic", result.first)
        assertEquals(3, result.second.size)
        assertTrue(result.second.any { it.type == SpanType.HEADING_1 })
        assertTrue(result.second.any { it.type == SpanType.BOLD })
        assertTrue(result.second.any { it.type == SpanType.ITALIC })
    }

    @Test
    fun passThroughPlainText() {
        val result = strip("hello world")

        assertEquals("hello world", result.first)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun handlesEmptyText() {
        val result = strip("")

        assertEquals("", result.first)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun handlesMultilineWithMultipleListItems() {
        val result = strip("- item 1\n- item 2")

        assertEquals("item 1\nitem 2", result.first)
        assertEquals(2, result.second.size)
        assertTrue(result.second.all { it.type == SpanType.BULLET_LIST })
    }

    private fun strip(text: String): Pair<String, List<SpanRange>> {
        val spans = parser.parse(text)
        return MarkdownStripper.strip(text, spans)
    }
}
