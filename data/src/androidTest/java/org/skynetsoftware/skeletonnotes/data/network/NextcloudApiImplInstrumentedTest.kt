package org.skynetsoftware.skeletonnotes.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.io.File

/**
 * Instrumented tests for [NextcloudApiImpl] that run against Android's built-in XML parser, which
 * behaves differently from the JVM (Xerces) parser used by unit tests. These guard the PROPFIND
 * parsing path — including the XXE hardening — on a real device.
 */
@RunWith(AndroidJUnit4::class)
class NextcloudApiImplInstrumentedTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var configStore: FakeNextcloudConfigStore
    private lateinit var api: NextcloudApiImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        configStore = FakeNextcloudConfigStore()
        configStore.serverUrlFlow.value = mockWebServer.url("/").toString().trimEnd('/')
        configStore.usernameFlow.value = "testuser"
        configStore.appPasswordFlow.value = "testpass"
        api = NextcloudApiImpl(configStore)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun listDirectoryParsesNormalResponseOnAndroidParser() = runBlocking {
        enqueueSyncFolderSuccess()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(propfindWithFiles())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )

        val result = api.listDirectory("")

        // The hardened parser must not throw on Android; a legitimate response parses successfully.
        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(2, files.size)
        assertEquals("note1.md", files[0].filename)
        assertEquals("note2.md", files[1].filename)
    }

    @Test
    fun listDirectoryDoesNotLeakXxePayloadOnAndroidParser() = runBlocking {
        val secretFile = File.createTempFile("xxe-secret", ".txt")
        secretFile.writeText("TOP_SECRET_CONTENTS")
        try {
            enqueueSyncFolderSuccess()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propfindWithXxe(secretFile.absolutePath))
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
            )

            val result = api.listDirectory("")

            val leaked = (result as? Result.Success)?.data.orEmpty()
                .any { it.filename.contains("TOP_SECRET_CONTENTS") }
            assertFalse("XXE payload must not leak local file contents", leaked)
        } finally {
            secretFile.delete()
        }
    }

    private fun enqueueSyncFolderSuccess() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(syncFolderPropfind())
                .setHeader("Content-Type", "application/xml; charset=utf-8")
        )
    }

    private fun syncFolderPropfind(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun propfindWithFiles(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/note1.md</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/note2.md</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>Wed, 15 May 2024 08:30:00 GMT</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private fun propfindWithXxe(secretFilePath: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE multistatus [ <!ENTITY xxe SYSTEM "file://$secretFilePath"> ]>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/.skeleton_notes/&xxe;.md</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>Mon, 01 Jan 2024 12:00:00 GMT</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }

    private class FakeNextcloudConfigStore : NextcloudConfigStore {
        val serverUrlFlow = MutableStateFlow<String?>(null)
        val usernameFlow = MutableStateFlow<String?>(null)
        val appPasswordFlow = MutableStateFlow<String?>(null)
        val isConfiguredFlow = MutableStateFlow(false)
        val periodicSyncEnabledFlow = MutableStateFlow(false)
        val lastSyncTimestampFlow = MutableStateFlow(0L)

        override val serverUrl: StateFlow<String?> = serverUrlFlow
        override val username: StateFlow<String?> = usernameFlow
        override val appPassword: StateFlow<String?> = appPasswordFlow
        override val isConfigured: Flow<Boolean> = isConfiguredFlow
        override val periodicSyncEnabled: StateFlow<Boolean> = periodicSyncEnabledFlow
        override val lastSyncTimestamp: StateFlow<Long> = lastSyncTimestampFlow

        override fun setServerConfig(serverUrl: String, username: String, appPassword: String) {}
        override fun clearServerConfig() {}
        override fun setPeriodicSyncEnabled(periodicSyncEnabled: Boolean) {}
        override fun setLastSyncTimestamp(lastSyncTimestamp: Long) {}
    }
}
