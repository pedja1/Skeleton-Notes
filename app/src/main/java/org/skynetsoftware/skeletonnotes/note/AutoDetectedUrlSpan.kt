package org.skynetsoftware.skeletonnotes.note

import android.text.style.URLSpan

/**
 * A [URLSpan] marker for auto-detected raw URLs (e.g. `https://example.com`) so the editor can
 * style them identically to Markdown-parsed links while keeping the serialization path aware that
 * they originated from a plain-text URL and should not be emitted as `[text](url)` Markdown.
 */
internal class AutoDetectedUrlSpan(
    url: String,
) : URLSpan(url)
