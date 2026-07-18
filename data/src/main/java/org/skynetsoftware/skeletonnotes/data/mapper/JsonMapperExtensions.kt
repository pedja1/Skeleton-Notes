package org.skynetsoftware.skeletonnotes.data.mapper

import org.json.JSONArray

/**
 * Reads a JSON array of strings into a [Set]. Shared by the backup and Nextcloud note codecs
 * for the `tags` array so the parsing cannot drift between the two JSON schemas.
 */
internal fun JSONArray.toStringSet(): Set<String> =
    buildSet {
        for (i in 0 until length()) {
            add(getString(i))
        }
    }
