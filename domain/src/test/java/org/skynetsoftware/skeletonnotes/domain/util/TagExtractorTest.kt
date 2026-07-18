package org.skynetsoftware.skeletonnotes.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagExtractorTest {
    @Test
    fun extractsSingleTag() {
        val tags = TagExtractor.extractTags("Hello #world")
        assertEquals(setOf("world"), tags)
    }

    @Test
    fun extractsMultipleTags() {
        val tags = TagExtractor.extractTags("#tag1 some text #tag2 more #tag3")
        assertEquals(setOf("tag1", "tag2", "tag3"), tags)
    }

    @Test
    fun extractsTagsWithUnderscores() {
        val tags = TagExtractor.extractTags("#hello_world #foo_bar")
        assertEquals(setOf("hello_world", "foo_bar"), tags)
    }

    @Test
    fun returnsEmptySetWhenNoTags() {
        val tags = TagExtractor.extractTags("No tags here at all")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun returnsEmptySetForEmptyContent() {
        val tags = TagExtractor.extractTags("")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun ignoresHashOnly() {
        val tags = TagExtractor.extractTags("text # text")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun handlesDuplicateTags() {
        val tags = TagExtractor.extractTags("#tag #tag #tag")
        assertEquals(setOf("tag"), tags)
    }

    @Test
    fun extractsTagsWithNumbers() {
        val tags = TagExtractor.extractTags("#tag1 #item2 #stuff3")
        assertEquals(setOf("tag1", "item2", "stuff3"), tags)
    }

    @Test
    fun extractsHashTagRegardlessOfPrefix() {
        val tags = TagExtractor.extractTags("text @#tag text")
        assertEquals(setOf("tag"), tags)
    }

    @Test
    fun extractsUnicodeTags() {
        val tags = TagExtractor.extractTags("Dinner at the #café with #über_friends and #日本語")
        assertEquals(setOf("café", "über_friends", "日本語"), tags)
    }

    @Test
    fun urlFragmentIsNotATag() {
        val tags = TagExtractor.extractTags("see https://example.com/page#section for details")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun hashInsideWordIsNotATag() {
        val tags = TagExtractor.extractTags("issue ABC#123 and item42#note")
        assertTrue(tags.isEmpty())
    }

    @Test
    fun doubleHashIsNotATag() {
        val tags = TagExtractor.extractTags("markdown heading ##NotATag")
        assertTrue(tags.isEmpty())
    }
}
