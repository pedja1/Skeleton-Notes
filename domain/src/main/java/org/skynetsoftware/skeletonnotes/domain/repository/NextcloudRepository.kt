package org.skynetsoftware.skeletonnotes.domain.repository

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import java.io.InputStream

/**
 * Repository responsible for communicating with the Nextcloud server
 * via WebDAV, including Login Flow v2 and file sync.
 */
interface NextcloudRepository {
    /**
     * Initiates the Nextcloud Login Flow v2. Returns the login URL to
     * open in a browser along with the poll token and endpoint.
     */
    suspend fun initiateLogin(serverUrl: String): Result<NextcloudInitiateLoginResult>

    /**
     * Makes a single poll request to check whether the user has completed
     * the browser-based login. On [NextcloudPollStatus.Authenticated], credentials
     * are persisted internally (the password is never leaked); otherwise
     * [NextcloudPollStatus.Pending] signals the caller to poll again.
     */
    suspend fun pollLogin(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus

    /**
     * Returns current connection info if credentials are stored, or null.
     */
    fun connectionInfo(): Flow<NextcloudConnectionInfo?>

    /**
     * Clears any stored credentials, effectively disconnecting.
     */
    fun logout()

    /**
     * Lists all note JSON files in the remote .skeleton_notes directory.
     */
    suspend fun listFiles(): Result<List<NextcloudFileInfo>>

    /**
     * Downloads and parses a note from the remote directory.
     */
    suspend fun downloadNote(uuid: String): Result<NextcloudNote>

    /**
     * Uploads a note to the remote directory.
     */
    suspend fun uploadNote(note: NextcloudNote): Result<Unit>

    /**
     * Deletes a note JSON file from the remote directory.
     */
    suspend fun deleteRemoteNote(uuid: String): Result<Unit>

    /**
     * Uploads an attachment file to the note's subdirectory.
     */
    suspend fun uploadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
        inputStream: InputStream,
        contentLength: Long,
    ): Result<Unit>

    /**
     * Downloads an attachment file from the note's subdirectory into [outputStream].
     */
    suspend fun downloadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<String>

    /**
     * Deletes an attachment file from the note's subdirectory.
     */
    suspend fun deleteAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<Unit>

    /**
     * Deletes the entire note directory including all attachment files.
     */
    suspend fun deleteNoteDirectory(uuid: String): Result<Unit>
}
