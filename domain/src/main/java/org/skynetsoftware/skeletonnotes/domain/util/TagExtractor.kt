package org.skynetsoftware.skeletonnotes.domain.util

/**
 * Utility for extracting hashtag-style tags from note content.
 * Tags are identified by the pattern `#word` and are not removed from the content.
 */
object TagExtractor {
    /**
     * Explicit Unicode property classes (`\p{L}\p{Nd}_`) instead of `\w` so non-ASCII tags
     * (`#café`, `#über`) are extracted whole on both the JVM (where `\w` is ASCII-only) and
     * Android (whose ICU regex rejects the `(?U)` flag with a runtime error). The lookbehind
     * rejects a `#` directly preceded by a word character or another `#`, so URL fragments
     * (`example.com/page#section`) and mid-word hashes do not produce spurious tags, while
     * non-word prefixes (`@#tag`) still count.
     */
    private val TAG_REGEX = Regex("(?<![\\p{L}\\p{Nd}_#])#([\\p{L}\\p{Nd}_]+)")

    /**
     * Extracts unique tags from [content] matching the pattern `#word`.
     * The `#` prefix is excluded from the extracted tag value.
     *
     * @param content the note content to scan for tags
     * @return a set of unique tag strings (without the `#` prefix)
     */
    fun extractTags(content: String): Set<String> = TAG_REGEX.findAll(content).map { it.groupValues[1] }.toSet()
}
