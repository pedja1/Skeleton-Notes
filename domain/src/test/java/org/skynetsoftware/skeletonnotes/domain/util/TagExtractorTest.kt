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
}
