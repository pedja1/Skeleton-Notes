package org.skynetsoftware.skeletonnotes.nextcloud.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.nextcloud.config.NextcloudConfigStore
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Communicates with Nextcloud via WebDAV and Login Flow v2 using OkHttp.
 */
internal class NextcloudApiImpl(
    private val nextcloudConfigStore: NextcloudConfigStore,
    appVersion: String,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NextcloudApi {
    companion object {
        private const val TAG = "NextcloudApi"
        private const val SYNC_FOLDER = ".skeleton_notes"
        private const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        private const val FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
        private const val FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities"
    }

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "Skeleton-Notes/$appVersion")
                        .build(),
                )
            }.build()

    /**
     * Dedicated client for the login poll that does not follow redirects, so a
     * reverse-proxy `302` on the "not done yet" response is treated as pending
     * instead of being followed to an HTML page.
     */
    private val pollHttpClient =
        httpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private fun authHeader(): String {
        val username = nextcloudConfigStore.username.value ?: error("Not logged in")
        val appPassword = nextcloudConfigStore.appPassword.value ?: error("Not logged in")
        val credentials = "$username:$appPassword"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private fun davBaseUrl(): String {
        val serverUrl = nextcloudConfigStore.serverUrl.value ?: error("Not logged in")
        val username = nextcloudConfigStore.username.value ?: error("Not logged in")
        return "${serverUrl.trimEnd('/')}/remote.php/dav/files/$username"
    }

    /**
     * Initiates the Nextcloud Login Flow v2 by posting to the server.
     * This call is anonymous and does not require authentication.
     */
    override suspend fun initiateLoginFlow(serverUrl: String): Result<NextcloudInitiateLoginResult> =
        withContext(
            coroutineDispatcher,
        ) {
            try {
                val normalizedUrl = serverUrl.trimEnd('/')
                val request =
                    Request
                        .Builder()
                        .url("$normalizedUrl/index.php/login/v2")
                        .post("".toRequestBody(null))
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body.string())
                        val poll = json.getJSONObject("poll")
                        val token = poll.getString("token")
                        val endpoint = poll.getString("endpoint")
                        val loginUrl = json.getString("login")
                        Result.Success(
                            NextcloudInitiateLoginResult(
                                token = token,
                                endpoint = endpoint,
                                loginUrl = loginUrl,
                            ),
                        )
                    } else {
                        Log.e(TAG, "initiateLoginFlow failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "initiateLoginFlow error", t)
                Result.Failure(t)
            }
        }

    /**
     * Makes a single poll request to check whether the user has completed
     * the browser-based login. A one-time `200` yields [NextcloudPollStatus.Authenticated];
     * every other response (including redirects and transient network errors) is
     * [NextcloudPollStatus.Pending] so the caller keeps polling.
     */
    override suspend fun pollLogin(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus =
        withContext(coroutineDispatcher) {
            try {
                val body = FormBody.Builder().add("token", token).build()
                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .post(body)
                        .build()

                val response = pollHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body.string())
                        val serverUrl = json.getString("server")
                        val username = json.getString("loginName")
                        val appPassword = json.getString("appPassword")
                        nextcloudConfigStore.setServerConfig(serverUrl, username, appPassword)
                        NextcloudPollStatus.Authenticated(
                            NextcloudConnectionInfo(serverUrl, username),
                        )
                    } else {
                        NextcloudPollStatus.Pending
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "pollLogin error", t)
                NextcloudPollStatus.Pending
            }
        }

    /**
     * Lists files in .skeleton_notes/[path] via PROPFIND with Depth: 1.
     * Automatically creates the sync folder if it does not exist.
     */
    override suspend fun listDirectory(path: String): Result<List<NextcloudFileInfo>> =
        withContext(coroutineDispatcher) {
            try {
                ensureSyncFolder()
                val url = "${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}"
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .method("PROPFIND", null)
                        .header("Authorization", authHeader())
                        .header("Depth", "1")
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.code in 200..207) {
                        val xml = resp.body.string()
                        parsePropfindResponse(xml, url)
                    } else {
                        Log.e(TAG, "listDirectory failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "listDirectory error", t)
                Result.Failure(t)
            }
        }

    /**
     * Downloads a file from .skeleton_notes/[path] via HTTP GET.
     */
    override suspend fun downloadFile(path: String): Result<ByteArray> =
        withContext(coroutineDispatcher) {
            val outputStream = ByteArrayOutputStream()
            when (val result = downloadFile(path, outputStream)) {
                is Result.Success -> Result.Success(outputStream.toByteArray())
                is Result.Failure -> Result.Failure(result.throwable)
            }
        }

    /**
     * Downloads a file from .skeleton_notes/[path] via HTTP GET into [outputStream].
     */
    override suspend fun downloadFile(
        path: String,
        outputStream: OutputStream,
    ): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val request =
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .get()
                        .header("Authorization", authHeader())
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        resp.body.byteStream().use { input ->
                            input.copyTo(outputStream)
                        }
                        Result.Success(Unit)
                    } else {
                        Log.e(TAG, "downloadFile failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "downloadFile error", t)
                Result.Failure(t)
            }
        }

    /**
     * Uploads a file to .skeleton_notes/[path] via HTTP PUT.
     */
    override suspend fun uploadFile(
        path: String,
        content: ByteArray,
        contentType: String,
    ): Result<Unit> =
        withContext(
            coroutineDispatcher,
        ) {
            uploadFile(path, content.inputStream(), content.size.toLong(), contentType)
        }

    /**
     * Uploads a file stream to .skeleton_notes/[path] via HTTP PUT.
     */
    override suspend fun uploadFile(
        path: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String,
    ): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val body = inputStream.toRequestBody(contentType, contentLength)
                val request =
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .put(body)
                        .header("Authorization", authHeader())
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.code in 200..204) {
                        Result.Success(Unit)
                    } else {
                        Log.e(TAG, "uploadFile failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "uploadFile error", t)
                Result.Failure(t)
            }
        }

    /** Creates an OkHttp [RequestBody] that streams from this [InputStream]. */
    private fun InputStream.toRequestBody(
        contentType: String,
        contentLength: Long,
    ): RequestBody =
        object : RequestBody() {
            override fun contentType() = contentType.toMediaType()

            override fun contentLength(): Long = contentLength

            override fun writeTo(sink: BufferedSink) {
                use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

    /**
     * Deletes a file from .skeleton_notes/[path] via HTTP DELETE.
     */
    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val request =
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .method("DELETE", null)
                        .header("Authorization", authHeader())
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    // 404 means the resource is already gone; treat delete as idempotent success.
                    if (resp.code in 200..204 || resp.code == 404) {
                        Result.Success(Unit)
                    } else {
                        Log.e(TAG, "deleteFile failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "deleteFile error", t)
                Result.Failure(t)
            }
        }

    /**
     * Creates a directory inside .skeleton_notes/[path] via WebDAV MKCOL.
     */
    override suspend fun createDirectory(path: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            try {
                val url = "${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}".trimEnd('/')
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .method("MKCOL", null)
                        .header("Authorization", authHeader())
                        .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.code in 200..204 || resp.code == 405) {
                        Result.Success(Unit)
                    } else {
                        Log.e(TAG, "createDirectory failed: ${resp.code} ${resp.body.string()}")
                        Result.Failure(Exception("Server returned ${resp.code}"))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "createDirectory error", t)
                Result.Failure(t)
            }
        }

    /**
     * Ensures the .skeleton_notes/ directory exists on the server,
     * creating it via MKCOL if necessary.
     */
    private suspend fun ensureSyncFolder() {
        try {
            val request =
                Request
                    .Builder()
                    .url("${davBaseUrl()}/$SYNC_FOLDER/")
                    .method("PROPFIND", null)
                    .header("Authorization", authHeader())
                    .header("Depth", "0")
                    .build()

            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.code == 404) {
                    createDirectory("")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ensureSyncFolder error", t)
        }
    }

    /**
     * Parses a PROPFIND multistatus XML response into a list of [NextcloudFileInfo].
     * Extracts href (filename) and getlastmodified for each response entry.
     */
    private fun parsePropfindResponse(
        xml: String,
        basePath: String,
    ): Result<List<NextcloudFileInfo>> =
        try {
            val builder = newSecureDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())

            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            val baseName = basePath.trimEnd('/').substringAfterLast('/')
            val files = mutableListOf<NextcloudFileInfo>()

            for (i in 0 until responses.length) {
                parseResponseEntry(responses.item(i) as Element, baseName)?.let { files.add(it) }
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
                lastModified = parseDavDate(lmNode.textContent)
            }
        }
        return NextcloudFileInfo(filename = filename, lastModified = lastModified)
    }

    /**
     * Parses a DAV date string into epoch milliseconds.
     * Supports RFC 1123 and ISO 8601 formats commonly used by Nextcloud.
     */
    private fun parseDavDate(dateString: String): Long {
        return try {
            val formats =
                listOf(
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    },
                )
            for (format in formats) {
                try {
                    return format.parse(dateString)?.time ?: continue
                } catch (_: Exception) {
                }
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }
}
