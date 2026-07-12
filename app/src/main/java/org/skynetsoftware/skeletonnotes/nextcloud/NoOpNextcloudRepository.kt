package org.skynetsoftware.skeletonnotes.nextcloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import java.io.InputStream

/**
 * No-op implementation of [NextcloudRepository] for the lite flavor.
 * All methods return failure or defaults, and [isSupported] returns `false`.
 */
class NoOpNextcloudRepository : NextcloudRepository {
    override fun isSupported(): Boolean = false

    override suspend fun initiateLogin(serverUrl: String): Result<NextcloudInitiateLoginResult> =
        Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun pollLogin(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus = NextcloudPollStatus.Pending

    override fun connectionInfo(): Flow<NextcloudConnectionInfo?> = MutableStateFlow(null)

    override fun logout() {}

    override suspend fun listFiles(): Result<List<NextcloudFileInfo>> = Result.Success(emptyList())

    override suspend fun downloadNote(uuid: String): Result<NextcloudNote> =
        Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun uploadNote(note: NextcloudNote): Result<Unit> =
        Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun deleteRemoteNote(uuid: String): Result<Unit> =
        Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun uploadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
        inputStream: InputStream,
        contentLength: Long,
    ): Result<Unit> = Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun downloadAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<String> = Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun deleteAttachment(
        noteId: String,
        attachmentId: String,
        filename: String,
    ): Result<Unit> = Result.Failure(UnsupportedOperationException("Nextcloud not supported"))

    override suspend fun deleteNoteDirectory(uuid: String): Result<Unit> =
        Result.Failure(UnsupportedOperationException("Nextcloud not supported"))
}
