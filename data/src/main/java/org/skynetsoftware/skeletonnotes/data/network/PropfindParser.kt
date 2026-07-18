package org.skynetsoftware.skeletonnotes.data.network

import android.util.Log
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private const val TAG = "PropfindParser"
private const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
private const val FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
private const val FEATURE_EXTERNAL_PARAMETER_ENTITIES =
    "http://xml.org/sax/features/external-parameter-entities"

/**
 * Parses a PROPFIND multistatus XML response into a list of [NextcloudFileInfo].
 * Extracts href (filename) and getlastmodified for each response entry.
 */
internal fun parsePropfindResponse(
    xml: String,
    basePath: String,
): Result<List<NextcloudFileInfo>> =
    try {
        val builder = newSecureDocumentBuilder()
        val doc = builder.parse(xml.byteInputStream())

        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        val baseName = basePath.trimEnd('/').substringAfterLast('/')
        val files = mutableListOf<NextcloudFileInfo>()

        // Created once per PROPFIND response instead of once per entry; kept method-local
        // because SimpleDateFormat is not thread-safe.
        val dateFormats = newDavDateFormats()
        for (i in 0 until responses.length) {
            parseResponseEntry(responses.item(i) as Element, baseName, dateFormats)?.let { files.add(it) }
        }
        Result.Success(files)
    } catch (t: Throwable) {
        Log.e(TAG, "parsePropfindResponse error", t)
        Result.Failure(t)
    }

/**
 * Creates a [DocumentBuilder] hardened against XML External Entity (XXE) attacks by disabling
 * external entity resolution and entity expansion, so a malicious or compromised server response
 * cannot trigger local-file disclosure or SSRF.
 *
 * Feature toggles are applied best-effort: Android's built-in parser does not implement the
 * Apache `disallow-doctype-decl` feature and throws [ParserConfigurationException] for it, so
 * unsupported features are skipped rather than aborting parsing. The runtime protection on
 * Android comes from disabling external general/parameter entities and not expanding entity
 * references (all supported on Android).
 */
private fun newSecureDocumentBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    trySetFeature(factory, FEATURE_DISALLOW_DOCTYPE, true)
    trySetFeature(factory, FEATURE_EXTERNAL_GENERAL_ENTITIES, false)
    trySetFeature(factory, FEATURE_EXTERNAL_PARAMETER_ENTITIES, false)
    try {
        factory.setXIncludeAware(false)
    } catch (e: UnsupportedOperationException) {
        Log.w(TAG, "setXIncludeAware unsupported", e)
    }
    factory.isExpandEntityReferences = false
    factory.isNamespaceAware = true
    return factory.newDocumentBuilder()
}

/**
 * Applies an XML parser [feature], ignoring parsers that do not support it (Android's parser
 * throws [ParserConfigurationException] for features such as `disallow-doctype-decl`).
 */
private fun trySetFeature(
    factory: DocumentBuilderFactory,
    feature: String,
    value: Boolean,
) {
    try {
        factory.setFeature(feature, value)
    } catch (e: ParserConfigurationException) {
        Log.w(TAG, "XML feature not supported: $feature", e)
    }
}

/**
 * Parses a single DAV `<response>` element into a [NextcloudFileInfo], returning null for the
 * base collection itself or for nested collections (directories), which are not note files.
 */
private fun parseResponseEntry(
    response: Element,
    baseName: String,
    dateFormats: List<SimpleDateFormat>,
): NextcloudFileInfo? {
    val href = response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: return null
    val filename = href.trimEnd('/').substringAfterLast('/')
    if (filename.isBlank() || filename == baseName) return null

    val resourcetypeNodes = response.getElementsByTagNameNS("DAV:", "resourcetype")
    if (resourcetypeNodes.length > 0) {
        val collectionNodes =
            (resourcetypeNodes.item(0) as? Element)
                ?.getElementsByTagNameNS("DAV:", "collection")
        if (collectionNodes != null && collectionNodes.length > 0) return null
    }

    val propstatList = response.getElementsByTagNameNS("DAV:", "propstat")
    var lastModified = 0L
    for (j in 0 until propstatList.length) {
        val propstat = propstatList.item(j) as Element
        val status = propstat.getElementsByTagNameNS("DAV:", "status").item(0)?.textContent ?: ""
        if (!status.contains("200 OK")) continue
        val prop = propstat.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element ?: continue
        val lmNode = prop.getElementsByTagNameNS("DAV:", "getlastmodified").item(0)
        if (lmNode != null) {
            lastModified = parseDavDate(lmNode.textContent, dateFormats)
        }
    }
    return NextcloudFileInfo(filename = filename, lastModified = lastModified)
}

/**
 * Creates the DAV date formats (RFC 1123 and the ISO 8601 variants commonly used by
 * Nextcloud) tried by [parseDavDate], in match-priority order.
 */
private fun newDavDateFormats(): List<SimpleDateFormat> =
    listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }

/**
 * Parses a DAV date string into epoch milliseconds using [dateFormats],
 * returning 0 when no format matches.
 */
private fun parseDavDate(
    dateString: String,
    dateFormats: List<SimpleDateFormat>,
): Long {
    for (format in dateFormats) {
        try {
            return format.parse(dateString)?.time ?: continue
        } catch (_: Exception) {
        }
    }
    return 0L
}
