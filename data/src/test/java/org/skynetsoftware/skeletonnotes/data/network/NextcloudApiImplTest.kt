package org.skynetsoftware.skeletonnotes.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import java.io.File

class NextcloudApiImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var configStore: FakeNextcloudConfigStore
    private lateinit var api: NextcloudApiImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        configStore = FakeNextcloudConfigStore()
        api = NextcloudApiImpl(configStore, "test")
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun configureAuthUser() {
        configStore.serverUrlFlow.value = mockWebServer.url("/").toString().trimEnd('/')
        configStore.usernameFlow.value = "testuser"
        configStore.appPasswordFlow.value = "testpass"
    }

    private fun enqueueSyncFolderSuccess() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(generateSyncFolderPropfindResponse())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )
    }

    // region initiateLoginFlow

    @Test
    fun initiateLoginFlowReturnsSuccessOn200(): Unit = runBlocking {
        val responseJson = """
            {
                "poll": {
                    "token": "abc123",
                    "endpoint": "${mockWebServer.url("/login/v2/poll")}"
                },
                "login": "https://cloud.example.com/login/v2/flow/abc123"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json")
        )

        val result = api.initiateLoginFlow(mockWebServer.url("/").toString().trimEnd('/'))

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals("abc123", data.token)
        assertTrue(data.endpoint.contains("/login/v2/poll"))
        assertEquals("https://cloud.example.com/login/v2/flow/abc123", data.loginUrl)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/index.php/login/v2", request.path)
    }

    @Test
    fun initiateLoginFlowReturnsFailureOnNon200(): Unit = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = api.initiateLoginFlow(mockWebServer.url("/").toString().trimEnd('/'))

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 500", (result as Result.Failure).throwable.message)
    }

    @Test
    fun initiateLoginFlowReturnsFailureOnNetworkError(): Unit = runBlocking {
        mockWebServer.shutdown()

        val result = api.initiateLoginFlow("http://127.0.0.1:1")

        assertTrue(result is Result.Failure)
    }

    // endregion

    // region pollLogin

    @Test
    fun pollLoginReturnsSuccessOn200AndStoresConfig(): Unit = runBlocking {
        val responseJson = """
            {
                "server": "https://cloud.example.com",
                "loginName": "testuser",
                "appPassword": "pass-token-xyz"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json")
        )

        val result = api.pollLogin("abc123", mockWebServer.url("/login/v2/poll").toString())

        assertTrue(result is NextcloudPollStatus.Authenticated)
        val data = (result as NextcloudPollStatus.Authenticated).info
        assertEquals("https://cloud.example.com", data.serverUrl)
        assertEquals("testuser", data.username)
        assertEquals("https://cloud.example.com", configStore.storedServerUrl)
        assertEquals("testuser", configStore.storedUsername)
        assertEquals("pass-token-xyz", configStore.storedAppPassword)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("token=abc123", request.body.readUtf8())
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type")?.split(";")?.get(0))
    }

    @Test
    fun pollLoginReturnsPendingOnNon200(): Unit = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = api.pollLogin("abc123", mockWebServer.url("/login/v2/poll").toString())

        assertTrue(result is NextcloudPollStatus.Pending)
    }

    @Test
    fun pollLoginReturnsPendingOnNetworkError(): Unit = runBlocking {
        mockWebServer.shutdown()

        val result = api.pollLogin(
            "abc123",
            "http://127.0.0.1:1/login/v2/poll"
        )

        assertTrue(result is NextcloudPollStatus.Pending)
    }

    // endregion

    // region listDirectory

    @Test
    fun listDirectoryReturnsFilesOnSuccess(): Unit = runBlocking {
        configureAuthUser()
        enqueueSyncFolderSuccess()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(generatePropfindResponseWithFiles())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )

        val result = api.listDirectory("")

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(2, files.size)
        assertEquals("note1.md", files[0].filename)
        assertEquals("note2.md", files[1].filename)
        assertTrue(files[0].lastModified > 0)
        assertTrue(files[1].lastModified > 0)

        val ensureRequest = mockWebServer.takeRequest()
        assertEquals("PROPFIND", ensureRequest.method)
        assertTrue(ensureRequest.path!!.contains(".skeleton_notes"))

        val listRequest = mockWebServer.takeRequest()
        assertEquals("PROPFIND", listRequest.method)
        assertEquals("1", listRequest.getHeader("Depth"))
    }

    @Test
    fun listDirectorySkipsCollectionsAndSelfRef(): Unit = runBlocking {
        configureAuthUser()
        enqueueSyncFolderSuccess()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(generatePropfindResponseWithCollection())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )

        val result = api.listDirectory("subdir")

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(1, files.size)
        assertEquals("note1.md", files[0].filename)
    }

    @Test
    fun listDirectoryCreatesSyncFolderWhenMissing(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(generatePropfindResponseWithFiles())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )

        val result = api.listDirectory("")

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(2, files.size)

        val ensureRequest = mockWebServer.takeRequest()
        assertEquals("PROPFIND", ensureRequest.method)
        assertEquals("0", ensureRequest.getHeader("Depth"))

        val mkcolRequest = mockWebServer.takeRequest()
        assertEquals("MKCOL", mkcolRequest.method)
    }

    @Test
    fun listDirectoryReturnsEmptyListForEmptyDirectory(): Unit = runBlocking {
        configureAuthUser()
        enqueueSyncFolderSuccess()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(generatePropfindResponseEmpty())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )

        val result = api.listDirectory("")

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertTrue(files.isEmpty())
    }

    @Test
    fun listDirectoryRejectsXxeExternalEntityPayload(): Unit = runBlocking {
        val secretFile = File.createTempFile("xxe-secret", ".txt")
        secretFile.writeText("TOP_SECRET_CONTENTS")
        try {
            configureAuthUser()
            enqueueSyncFolderSuccess()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(generatePropfindResponseWithXxe(secretFile.absolutePath))
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
            )

            val result = api.listDirectory("")

            // The external entity must never be resolved, so the secret file contents must not
            // appear in any parsed filename. (On the JVM/Xerces parser the DOCTYPE is rejected
            // outright; on Android the entity is simply left unexpanded — either way, no leak.)
            val leaked = (result as? Result.Success)?.data.orEmpty()
                .any { it.filename.contains("TOP_SECRET_CONTENTS") }
            assertTrue("XXE payload must not leak local file contents", !leaked)
        } finally {
            secretFile.delete()
        }
    }

    @Test
    fun listDirectoryReturnsFailureOnServerError(): Unit = runBlocking {
        configureAuthUser()
        enqueueSyncFolderSuccess()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = api.listDirectory("")

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 500", (result as Result.Failure).throwable.message)
    }

    // endregion

    // region downloadFile

    @Test
    fun downloadFileReturnsBytesOnSuccess(): Unit = runBlocking {
        configureAuthUser()
        val fileContent = "Hello, Nextcloud!".toByteArray()
        val buffer = okio.Buffer()
        buffer.write(fileContent)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/markdown")
                .setBody(buffer)
        )

        val result = api.downloadFile("notes/note1.md")

        assertTrue(result is Result.Success)
        assertArrayEquals(fileContent, (result as Result.Success).data)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.contains("notes/note1.md"))
    }

    @Test
    fun downloadFileReturnsFailureOnServerError(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = api.downloadFile("missing.md")

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 404", (result as Result.Failure).throwable.message)
    }

    // endregion

    // region uploadFile

    @Test
    fun uploadFileReturnsSuccessOn201(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val content = "Uploaded content".toByteArray()
        val result = api.uploadFile("notes/new.md", content, "text/markdown")

        assertTrue(result is Result.Success)

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.contains("notes/new.md"))
        assertEquals("text/markdown", request.getHeader("Content-Type"))
        assertArrayEquals(content, request.body.readByteArray())
    }

    @Test
    fun uploadFileReturnsSuccessOn204(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val result = api.uploadFile("notes/update.md", "content".toByteArray(), "text/markdown")

        assertTrue(result is Result.Success)
    }

    @Test
    fun uploadFileReturnsFailureOnServerError(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = api.uploadFile("notes/fail.md", "content".toByteArray(), "text/markdown")

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 500", (result as Result.Failure).throwable.message)
    }

    // endregion

    // region deleteFile

    @Test
    fun deleteFileReturnsSuccessOn204(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val result = api.deleteFile("notes/old.md")

        assertTrue(result is Result.Success)

        val request = mockWebServer.takeRequest()
        assertEquals("DELETE", request.method)
        assertTrue(request.path!!.contains("notes/old.md"))
    }

    @Test
    fun deleteFileReturnsSuccessOn200(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = api.deleteFile("notes/old.md")

        assertTrue(result is Result.Success)
    }

    @Test
    fun deleteFileReturnsSuccessOn404AlreadyGone(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = api.deleteFile("notes/missing.md")

        assertTrue(result is Result.Success)
    }

    @Test
    fun deleteFileReturnsFailureOnServerError(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = api.deleteFile("notes/fail.md")

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 500", (result as Result.Failure).throwable.message)
    }

    // endregion

    // region createDirectory

    @Test
    fun createDirectoryReturnsSuccessOn201(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val result = api.createDirectory("newfolder")

        assertTrue(result is Result.Success)

        val request = mockWebServer.takeRequest()
        assertEquals("MKCOL", request.method)
        assertTrue(request.path!!.contains("newfolder"))
    }

    @Test
    fun createDirectoryReturnsSuccessOn405AlreadyExists(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(405))

        val result = api.createDirectory("existing")

        assertTrue(result is Result.Success)
    }

    @Test
    fun createDirectoryReturnsFailureOnOtherError(): Unit = runBlocking {
        configureAuthUser()
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = api.createDirectory("badfolder")

        assertTrue(result is Result.Failure)
        assertEquals("Server returned 500", (result as Result.Failure).throwable.message)
    }

    // endregion

    // region XML helpers

    private fun generateSyncFolderPropfindResponse(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun generatePropfindResponseWithFiles(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/note1.md</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/note2.md</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Wed, 15 May 2024 08:30:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun generatePropfindResponseWithCollection(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/subdir/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/subdir/note1.md</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/subdir/images/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun generatePropfindResponseWithXxe(secretFilePath: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE multistatus [ <!ENTITY xxe SYSTEM "file://$secretFilePath"> ]>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/&xxe;.md</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun generatePropfindResponseEmpty(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    // endregion
}

private class FakeNextcloudConfigStore : NextcloudConfigStore {

    val serverUrlFlow = MutableStateFlow<String?>(null)
    val usernameFlow = MutableStateFlow<String?>(null)
    val appPasswordFlow = MutableStateFlow<String?>(null)
    val isConfiguredFlow = MutableStateFlow(false)
    val periodicSyncEnabledFlow = MutableStateFlow(false)
    val lastSyncTimestampFlow = MutableStateFlow(0L)

    var storedServerUrl: String? = null
    var storedUsername: String? = null
    var storedAppPassword: String? = null
    var clearServerConfigCalled = false
    var setPeriodicSyncEnabledValue: Boolean? = null
    var setLastSyncTimestampValue: Long? = null

    override val serverUrl: StateFlow<String?> = serverUrlFlow
    override val username: StateFlow<String?> = usernameFlow
    override val appPassword: StateFlow<String?> = appPasswordFlow
    override val isConfigured: Flow<Boolean> = isConfiguredFlow
    override val periodicSyncEnabled: StateFlow<Boolean> = periodicSyncEnabledFlow
    override val lastSyncTimestamp: StateFlow<Long> = lastSyncTimestampFlow

    override fun setServerConfig(serverUrl: String, username: String, appPassword: String) {
        storedServerUrl = serverUrl
        storedUsername = username
        storedAppPassword = appPassword
    }

    override fun clearServerConfig() {
        clearServerConfigCalled = true
    }

    override fun setPeriodicSyncEnabled(periodicSyncEnabled: Boolean) {
        setPeriodicSyncEnabledValue = periodicSyncEnabled
    }

    override fun setLastSyncTimestamp(lastSyncTimestamp: Long) {
        setLastSyncTimestampValue = lastSyncTimestamp
    }

    override val syncIntervalMinutes: StateFlow<Long> = MutableStateFlow(360L)
    override val syncOnlyOnUnmetered: StateFlow<Boolean> = MutableStateFlow(true)
    override fun setSyncIntervalMinutes(minutes: Long) {}
    override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}
}
