package org.skynetsoftware.skeletonnotes.data.network

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import java.io.InputStream
import java.io.OutputStream

/**
 * Internal interface for raw HTTP operations against the Nextcloud server.
 * Provides Login Flow v2 authentication and WebDAV file operations.
 */
internal interface NextcloudApi {

    /**
     * Initiates the Nextcloud Login Flow v2 by posting to the server.
     * This call is anonymous and does not require authentication.
     */
    suspend fun initiateLoginFlow(serverUrl: String): Result<NextcloudInitiateLoginResult>

    /**
     * Makes a single poll request to check whether the user has completed
     * the browser-based login. On [NextcloudPollStatus.Authenticated],
     * credentials are persisted. Any non-final response is [NextcloudPollStatus.Pending].
     */
    suspend fun pollLogin(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus

    /**
     * Lists files in the given WebDAV [path] inside .skeleton_notes/ using PROPFIND.
     *
     * @param path relative path inside the sync folder
     * @return list of [RemoteFileInfo] with filenames and last-modified timestamps
     */
    suspend fun listDirectory(path: String): Result<List<NextcloudFileInfo>>

    /**
     * Downloads a file from .skeleton_notes/[path] via HTTP GET.
     *
     * @param path relative path inside the sync folder
     * @return raw file bytes
     */
    suspend fun downloadFile(path: String): Result<ByteArray>

    /**
     * Downloads a file from .skeleton_notes/[path] via HTTP GET into [outputStream].
     *
     * @param path relative path inside the sync folder
     * @param outputStream destination stream for raw file bytes
     */
    suspend fun downloadFile(path: String, outputStream: OutputStream): Result<Unit>

    /**
     * Uploads a file to .skeleton_notes/[path] via HTTP PUT.
     *
     * @param path relative path inside the sync folder
     * @param content raw file bytes
     * @param contentType MIME type for the Content-Type header
     */
    suspend fun uploadFile(path: String, content: ByteArray, contentType: String): Result<Unit>

    /**
     * Uploads a file stream to .skeleton_notes/[path] via HTTP PUT.
     *
     * @param path relative path inside the sync folder
     * @param inputStream source stream for raw file bytes
     * @param contentLength number of bytes to upload, or `-1` when unknown
     * @param contentType MIME type for the Content-Type header
     */
    suspend fun uploadFile(
        path: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String,
    ): Result<Unit>

    /**
     * Deletes a file from .skeleton_notes/[path] via HTTP DELETE.
     *
     * @param path relative path inside the sync folder
     */
    suspend fun deleteFile(path: String): Result<Unit>

    /**
     * Creates a directory inside .skeleton_notes/[path] via WebDAV MKCOL.
     *
     * @param path relative path inside the sync folder
     */
    suspend fun createDirectory(path: String): Result<Unit>
}
