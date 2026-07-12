package org.skynetsoftware.skeletonnotes.nextcloud.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.nextcloud.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.nextcloud.mapper.toJson
import org.skynetsoftware.skeletonnotes.nextcloud.network.NextcloudApi
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class NextcloudRepositoryImplTest {
    private lateinit var api: FakeNextcloudApi
    private lateinit var configStore: FakeNextcloudConfigStore
    private lateinit var fakeAttachmentStorage: FakeAttachmentStorage
    private lateinit var repository: NextcloudRepositoryImpl

    @Before
    fun setUp() {
        api = FakeNextcloudApi()
        configStore = FakeNextcloudConfigStore()
        fakeAttachmentStorage = FakeAttachmentStorage()
        repository = NextcloudRepositoryImpl(api, configStore, fakeAttachmentStorage)
    }

    @Test
    fun initiateLoginDelegatesToApi() =
        runBlocking {
            val expected = NextcloudInitiateLoginResult("token", "endpoint", "https://login.url")
            api.initiateLoginResult = Result.Success(expected)

            val result = repository.initiateLogin("https://cloud.example.com")

            assertEquals("https://cloud.example.com", api.lastInitiateLoginServerUrl)
            assertTrue(result is Result.Success)
            assertEquals(expected, (result as Result.Success).data)
        }

    @Test
    fun pollLoginDelegatesToApi() =
        runBlocking {
            val expected = NextcloudConnectionInfo("https://cloud.example.com", "user")
            api.pollLoginResult = NextcloudPollStatus.Authenticated(expected)

            val result = repository.pollLogin("token", "endpoint")

            assertEquals("token", api.lastPollLoginToken)
            assertEquals("endpoint", api.lastPollLoginEndpoint)
            assertTrue(result is NextcloudPollStatus.Authenticated)
            assertEquals(expected, (result as NextcloudPollStatus.Authenticated).info)
        }

    @Test
    fun connectionInfoReturnsConnectionWhenConfigured() =
        runBlocking {
            configStore.serverUrlFlow.value = "https://cloud.example.com"
            configStore.usernameFlow.value = "user"

            val value = repository.connectionInfo().first()

            assertNotNull(value)
            assertEquals("https://cloud.example.com", value?.serverUrl)
            assertEquals("user", value?.username)
        }

    @Test
    fun connectionInfoReturnsNullWhenNotConfigured() =
        runBlocking {
            configStore.serverUrlFlow.value = null
            configStore.usernameFlow.value = null

            val value = repository.connectionInfo().first()

            assertNull(value)
        }

    @Test
    fun logoutClearsConfig() {
        repository.logout()

        assertTrue(configStore.clearServerConfigCalled)
    }

    @Test
    fun listFilesDelegatesToApi() =
        runBlocking {
            val expected = listOf(NextcloudFileInfo("note1.json", 1000L))
            api.listDirectoryResult = Result.Success(expected)

            val result = repository.listFiles()

            assertEquals("/", api.lastListDirectoryPath)
            assertTrue(result is Result.Success)
            assertEquals(expected, (result as Result.Success).data)
        }

    @Test
    fun downloadNoteReturnsParsedNote() =
        runBlocking {
            val note =
                NextcloudNote(
                    id = "note1",
                    title = "Test",
                    content = "Content",
                    createdAt = 1000L,
                    modifiedAt = 2000L,
                    tags = emptySet(),
                    status = "active",
                    attachments = emptyList(),
                )
            val json = note.toJson()
            api.downloadFileResult = Result.Success(json.toByteArray(Charsets.UTF_8))

            val result = repository.downloadNote("note1")

            assertEquals("note1.json", api.lastDownloadFilePath)
            assertTrue(result is Result.Success)
            assertEquals("note1", (result as Result.Success).data.id)
            assertEquals("Test", result.data.title)
        }

    @Test
    fun downloadNotePropagatesFailure() =
        runBlocking {
            api.downloadFileResult = Result.Failure(Exception("download failed"))

            val result = repository.downloadNote("note1")

            assertTrue(result is Result.Failure)
        }

    @Test
    fun uploadNoteSerializesAndUploads() =
        runBlocking {
            val note =
                NextcloudNote(
                    id = "n1",
                    title = "T",
                    content = "C",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = emptySet(),
                    status = "active",
                    attachments = emptyList(),
                )
            api.uploadFileResult = Result.Success(Unit)

            val result = repository.uploadNote(note)

            assertEquals("n1.json", api.lastUploadFilePath)
            assertEquals("application/json", api.lastUploadContentType)
            assertTrue(result is Result.Success)
        }

    @Test
    fun deleteRemoteNoteDelegatesToApi() =
        runBlocking {
            api.deleteFileResult = Result.Success(Unit)

            val result = repository.deleteRemoteNote("note1")

            assertEquals("note1.json", api.lastDeleteFilePath)
            assertTrue(result is Result.Success)
        }

    @Test
    fun uploadAttachmentCreatesDirectoryAndUploads() =
        runBlocking {
            api.createDirectoryResult = Result.Success(Unit)
            api.uploadFileResult = Result.Success(Unit)

            val result =
                repository.uploadAttachment(
                    "note1",
                    "att1",
                    "file.png",
                    byteArrayOf(1, 2, 3).inputStream(),
                    3L,
                )

            assertEquals("note1/", api.lastCreateDirectoryPath)
            assertEquals("note1/att1_file.png", api.lastUploadFilePath)
            assertEquals("application/octet-stream", api.lastUploadContentType)
            assertTrue(api.uploadStreamCalled)
            assertTrue(result is Result.Success)
        }

    @Test
    fun uploadAttachmentReturnsFailureWhenCreateDirectoryFails() =
        runBlocking {
            api.createDirectoryResult = Result.Failure(Exception("mkcol failed"))
            api.uploadFileResult = Result.Success(Unit)

            val result = repository.uploadAttachment("note1", "att1", "file.png", byteArrayOf(1).inputStream(), 1L)

            assertTrue(result is Result.Failure)
            // Upload must not be attempted when the directory could not be created.
            assertNull(api.lastUploadFilePath)
        }

    @Test
    fun downloadAttachmentDelegatesToApi() =
        runBlocking {
            val expected = byteArrayOf(4, 5, 6)
            api.downloadFileResult = Result.Success(expected)

            val result = repository.downloadAttachment("note1", "att1", "file.png")

            assertEquals("note1/att1_file.png", api.lastDownloadFilePath)
            assertTrue(result is Result.Success)
            assertEquals(expected.toList(), fakeAttachmentStorage.getWritten("att1").toList())
            assertTrue(api.downloadStreamCalled)
        }

    @Test
    fun downloadAttachmentSanitizesPathTraversalFilename() =
        runBlocking {
            api.downloadFileResult = Result.Success(byteArrayOf(1))

            repository.downloadAttachment("note1", "att1", "../../../etc/passwd")

            val path = api.lastDownloadFilePath
            assertNotNull(path)
            assertFalse(path!!.contains(".."))
            assertEquals("note1/att1_passwd", path)
        }

    @Test
    fun deleteAttachmentDelegatesToApi() =
        runBlocking {
            api.deleteFileResult = Result.Success(Unit)

            val result = repository.deleteAttachment("note1", "att1", "file.png")

            assertEquals("note1/att1_file.png", api.lastDeleteFilePath)
            assertTrue(result is Result.Success)
        }

    @Test
    fun deleteNoteDirectoryDeletesDirectoryAndFile() =
        runBlocking {
            api.deleteFileResult = Result.Success(Unit)

            val result = repository.deleteNoteDirectory("note1")

            assertTrue(api.deletedFilePaths.contains("note1/"))
            assertTrue(api.deletedFilePaths.contains("note1.json"))
            assertTrue(result is Result.Success)
        }

    @Test
    fun deleteRemoteNoteDirectoryReturnsFailureWhenDirectoryDeleteFails() =
        runBlocking {
            api.deleteFileResult = Result.Failure(Exception("delete failed"))

            val result = repository.deleteNoteDirectory("note1")

            assertTrue(result is Result.Failure)
        }

    private class FakeAttachmentStorage : AttachmentFileStorage {
        private val written = mutableMapOf<String, ByteArray>()

        fun getWritten(attachmentId: String): ByteArray = written[attachmentId] ?: byteArrayOf()

        override fun copyToStorage(
            source: String,
            attachmentId: String,
            mimeType: String?,
        ) = "/fake/$attachmentId"

        override fun writeStream(
            attachmentId: String,
            inputStream: InputStream,
        ): String {
            written[attachmentId] = inputStream.readBytes()
            return "/fake/$attachmentId"
        }

        override fun openWriteStream(
            attachmentId: String,
            extension: String?,
        ): AttachmentWriteTarget {
            val baos =
                object : java.io.ByteArrayOutputStream() {
                    override fun close() {
                        written[attachmentId] = toByteArray()
                        super.close()
                    }
                }
            return AttachmentWriteTarget("/fake/$attachmentId", baos)
        }

        override fun getFile(attachmentId: String) = File("/fake/$attachmentId")

        override fun deleteFile(attachmentId: String) {
            written.remove(attachmentId)
        }
    }

    private class FakeNextcloudApi : NextcloudApi {
        var initiateLoginResult: Result<NextcloudInitiateLoginResult> =
            Result.Failure(Exception("not set"))
        var pollLoginResult: NextcloudPollStatus = NextcloudPollStatus.Pending
        var listDirectoryResult: Result<List<NextcloudFileInfo>> =
            Result.Failure(Exception("not set"))
        var downloadFileResult: Result<ByteArray> = Result.Failure(Exception("not set"))
        var uploadFileResult: Result<Unit> = Result.Failure(Exception("not set"))
        var deleteFileResult: Result<Unit> = Result.Failure(Exception("not set"))
        var createDirectoryResult: Result<Unit> = Result.Failure(Exception("not set"))
        var uploadStreamCalled = false
        var downloadStreamCalled = false

        var lastInitiateLoginServerUrl: String? = null
        var lastPollLoginToken: String? = null
        var lastPollLoginEndpoint: String? = null
        var lastListDirectoryPath: String? = null
        var lastDownloadFilePath: String? = null
        var lastUploadFilePath: String? = null
        var lastUploadContentType: String? = null
        var lastDeleteFilePath: String? = null
        var lastCreateDirectoryPath: String? = null
        val deletedFilePaths = mutableListOf<String>()

        override suspend fun initiateLoginFlow(serverUrl: String): Result<NextcloudInitiateLoginResult> {
            lastInitiateLoginServerUrl = serverUrl
            return initiateLoginResult
        }

        override suspend fun pollLogin(
            token: String,
            endpoint: String,
        ): NextcloudPollStatus {
            lastPollLoginToken = token
            lastPollLoginEndpoint = endpoint
            return pollLoginResult
        }

        override suspend fun listDirectory(path: String): Result<List<NextcloudFileInfo>> {
            lastListDirectoryPath = path
            return listDirectoryResult
        }

        override suspend fun downloadFile(path: String): Result<ByteArray> {
            lastDownloadFilePath = path
            return downloadFileResult
        }

        override suspend fun downloadFile(
            path: String,
            outputStream: OutputStream,
        ): Result<Unit> {
            lastDownloadFilePath = path
            downloadStreamCalled = true
            return when (val result = downloadFileResult) {
                is Result.Success -> {
                    @Suppress("BlockingMethodInNonBlockingContext") // fine for tests
                    outputStream.write(result.data)
                    Result.Success(Unit)
                }
                is Result.Failure -> Result.Failure(result.throwable)
            }
        }

        override suspend fun uploadFile(
            path: String,
            content: ByteArray,
            contentType: String,
        ): Result<Unit> {
            lastUploadFilePath = path
            lastUploadContentType = contentType
            return uploadFileResult
        }

        override suspend fun uploadFile(
            path: String,
            inputStream: InputStream,
            contentLength: Long,
            contentType: String,
        ): Result<Unit> {
            lastUploadFilePath = path
            lastUploadContentType = contentType
            uploadStreamCalled = true
            inputStream.readBytes()
            return uploadFileResult
        }

        override suspend fun deleteFile(path: String): Result<Unit> {
            lastDeleteFilePath = path
            deletedFilePaths.add(path)
            return deleteFileResult
        }

        override suspend fun createDirectory(path: String): Result<Unit> {
            lastCreateDirectoryPath = path
            return createDirectoryResult
        }
    }

    private class FakeNextcloudConfigStore : NextcloudConfigStore {
        val serverUrlFlow = MutableStateFlow<String?>(null)
        val usernameFlow = MutableStateFlow<String?>(null)
        val appPasswordFlow = MutableStateFlow<String?>(null)
        val isConfiguredFlow = MutableStateFlow(false)
        val periodicSyncEnabledFlow = MutableStateFlow(false)
        val lastSyncTimestampFlow = MutableStateFlow(0L)

        var clearServerConfigCalled = false
        var setPeriodicSyncEnabledValue: Boolean? = null
        var setLastSyncTimestampValue: Long? = null

        override val serverUrl: StateFlow<String?> = serverUrlFlow
        override val username: StateFlow<String?> = usernameFlow
        override val appPassword: StateFlow<String?> = appPasswordFlow
        override val isConfigured: Flow<Boolean> = isConfiguredFlow
        override val periodicSyncEnabled: StateFlow<Boolean> = periodicSyncEnabledFlow
        override val lastSyncTimestamp: StateFlow<Long> = lastSyncTimestampFlow

        override fun setServerConfig(
            serverUrl: String,
            username: String,
            appPassword: String,
        ) {}

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
}
