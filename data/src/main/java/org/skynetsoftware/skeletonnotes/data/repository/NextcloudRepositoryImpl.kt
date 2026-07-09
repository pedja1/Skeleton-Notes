package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.data.mapper.jsonToNextcloudNote
import org.skynetsoftware.skeletonnotes.data.mapper.nextcloudNoteToJson
import org.skynetsoftware.skeletonnotes.data.network.NextcloudApi
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.RemoteFileInfo

/**
 * Implementation of [NextcloudRepository] that uses [NextcloudApi]
 * to interact with the Nextcloud WebDAV API.
 */
internal class NextcloudRepositoryImpl(
    private val nextcloudApi: NextcloudApi,
    private val nextcloudConfigStore: NextcloudConfigStore,
) : NextcloudRepository {

    override suspend fun initiateLogin(serverUrl: String) = nextcloudApi.initiateLoginFlow(serverUrl)

    override suspend fun pollLogin(
        token: String,
        endpoint: String,
    ) = nextcloudApi.pollLogin(token, endpoint)

    override fun connectionInfo(): Flow<NextcloudConnectionInfo?> {
        return combine(
            nextcloudConfigStore.serverUrl,
            nextcloudConfigStore.username,
        ) { serverUrl, username ->
            if (serverUrl != null && username != null) {
                NextcloudConnectionInfo(serverUrl, username)
            } else {
                null
            }
        }
    }

    override fun logout() = nextcloudConfigStore.clearServerConfig()

    /**
     * Lists all note JSON files in .skeleton_notes/ via WebDAV PROPFIND.
     */
    override suspend fun listRemoteFiles(): Result<List<RemoteFileInfo>> {
        return nextcloudApi.listDirectory("/")
    }

    /**
     * Downloads a note JSON file and deserializes it into a [NextcloudNote].
     */
    override suspend fun downloadNote(uuid: String): Result<NextcloudNote> {
        return when (val result = nextcloudApi.downloadFile("$uuid.json")) {
            is Result.Success -> Result.Success(jsonToNextcloudNote(String(result.data, Charsets.UTF_8)))
            is Result.Failure -> Result.Failure(result.throwable)
        }
    }

    /**
     * Serializes a [NextcloudNote] to JSON and uploads it to the server.
     */
    override suspend fun uploadNote(note: NextcloudNote): Result<Unit> {
        val json = nextcloudNoteToJson(note)
        return nextcloudApi.uploadFile(
            "${note.id}.json",
            json.toByteArray(Charsets.UTF_8),
            "application/json",
        )
    }

    /**
     * Deletes a note JSON file from the server.
     */
    override suspend fun deleteRemoteNote(uuid: String): Result<Unit> {
        return nextcloudApi.deleteFile("$uuid.json")
    }

    /**
     * Uploads an attachment file to the note's subdirectory on the server.
     * Creates the note directory if it does not exist.
     */
    override suspend fun uploadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
        bytes: ByteArray,
    ): Result<Unit> {
        nextcloudApi.createDirectory("$noteId/")
        return nextcloudApi.uploadFile(
            "$noteId/${attachmentId}_$filename",
            bytes,
            "application/octet-stream",
        )
    }

    /**
     * Downloads an attachment file from the note's subdirectory on the server.
     */
    override suspend fun downloadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<ByteArray> {
        return nextcloudApi.downloadFile("$noteId/${attachmentId}_$filename")
    }

    /**
     * Deletes an attachment file from the note's subdirectory on the server.
     */
    override suspend fun deleteRemoteAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<Unit> {
        return nextcloudApi.deleteFile("$noteId/${attachmentId}_$filename")
    }

    /**
     * Deletes the entire note directory including all attachment files and the JSON file.
     */
    override suspend fun deleteRemoteNoteDirectory(uuid: String): Result<Unit> {
        nextcloudApi.deleteFile("$uuid/")
        return nextcloudApi.deleteFile("$uuid.json")
    }
}
