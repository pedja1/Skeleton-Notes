package org.skynetsoftware.skeletonnotes.data.network

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
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudHttpException
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

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
    }

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .setNextcloudTlsSocketFactory()
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
            .setNextcloudTlsSocketFactory()
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
     * Runs one HTTP call end to end: builds the request via [buildRequest], executes it on
     * [client], treats status codes matching [isSuccess] as success (delegating to [onSuccess]),
     * and maps any other code to a logged [NextcloudHttpException] failure. Everything thrown —
     * request building (including the "not logged in" errors from [davBaseUrl]/[authHeader]),
     * network I/O, and body parsing — becomes a [Result.Failure], so every endpoint shares a
     * single error path.
     */
    private inline fun <T> execute(
        opName: String,
        client: OkHttpClient = httpClient,
        isSuccess: (Int) -> Boolean = { it in 200..204 },
        buildRequest: () -> Request,
        onSuccess: (Response) -> Result<T>,
    ): Result<T> =
        try {
            client.newCall(buildRequest()).execute().use { resp ->
                if (isSuccess(resp.code)) {
                    onSuccess(resp)
                } else {
                    Log.e(TAG, "$opName failed: ${resp.code} ${resp.body.string()}")
                    Result.Failure(NextcloudHttpException(resp.code))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "$opName error", t)
            Result.Failure(t)
        }

    /**
     * Initiates the Nextcloud Login Flow v2 by posting to the server.
     * This call is anonymous and does not require authentication.
     */
    override suspend fun initiateLoginFlow(serverUrl: String): Result<NextcloudInitiateLoginResult> =
        withContext(coroutineDispatcher) {
            execute(
                "initiateLoginFlow",
                isSuccess = { it in 200..299 },
                buildRequest = {
                    Request
                        .Builder()
                        .url("${serverUrl.trimEnd('/')}/index.php/login/v2")
                        .post("".toRequestBody(null))
                        .build()
                },
            ) { resp ->
                val json = JSONObject(resp.body.string())
                val poll = json.getJSONObject("poll")
                Result.Success(
                    NextcloudInitiateLoginResult(
                        token = poll.getString("token"),
                        endpoint = poll.getString("endpoint"),
                        loginUrl = json.getString("login"),
                    ),
                )
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
            ensureSyncFolder()
            val basePath = "$SYNC_FOLDER/${path.trimStart('/')}"
            execute(
                "listDirectory",
                isSuccess = { it in 200..207 },
                buildRequest = {
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$basePath")
                        .method("PROPFIND", null)
                        .header("Authorization", authHeader())
                        .header("Depth", "1")
                        .build()
                },
            ) { resp -> parsePropfindResponse(resp.body.string(), basePath) }
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
            execute(
                "downloadFile",
                isSuccess = { it in 200..299 },
                buildRequest = {
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .get()
                        .header("Authorization", authHeader())
                        .build()
                },
            ) { resp ->
                resp.body.byteStream().use { input ->
                    input.copyTo(outputStream)
                }
                Result.Success(Unit)
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
            execute(
                "uploadFile",
                buildRequest = {
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .put(inputStream.toRequestBody(contentType, contentLength))
                        .header("Authorization", authHeader())
                        .build()
                },
            ) { Result.Success(Unit) }
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
            execute(
                "deleteFile",
                // 404 means the resource is already gone; treat delete as idempotent success.
                isSuccess = { it in 200..204 || it == 404 },
                buildRequest = {
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}")
                        .method("DELETE", null)
                        .header("Authorization", authHeader())
                        .build()
                },
            ) { Result.Success(Unit) }
        }

    /**
     * Creates a directory inside .skeleton_notes/[path] via WebDAV MKCOL.
     */
    override suspend fun createDirectory(path: String): Result<Unit> =
        withContext(coroutineDispatcher) {
            execute(
                "createDirectory",
                // 405 means the collection already exists; creating it is idempotent.
                isSuccess = { it in 200..204 || it == 405 },
                buildRequest = {
                    Request
                        .Builder()
                        .url("${davBaseUrl()}/$SYNC_FOLDER/${path.trimStart('/')}".trimEnd('/'))
                        .method("MKCOL", null)
                        .header("Authorization", authHeader())
                        .build()
                },
            ) { Result.Success(Unit) }
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
}
