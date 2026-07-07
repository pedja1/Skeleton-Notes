package org.skynetsoftware.skeletonnotes.domain.util

/**
 * Utility for extracting hashtag-style tags from note content.
 * Tags are identified by the pattern `#word` and are not removed from the content.
 */
object TagExtractor {
    private val TAG_REGEX = Regex("#(\\w+)")

    /**
     * Extracts unique tags from [content] matching the pattern `#word`.
     * The `#` prefix is excluded from the extracted tag value.
     *
     * @param content the note content to scan for tags
     * @return a set of unique tag strings (without the `#` prefix)
     */
    fun extractTags(content: String): Set<String> = TAG_REGEX.findAll(content).map { it.groupValues[1] }.toSet()
}
