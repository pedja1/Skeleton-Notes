package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Result
import java.util.UUID

class CreateAttachmentUseCase(
    private val attachmentFileStorage: AttachmentFileStorage,
) {
    suspend operator fun invoke(
        noteId: String,
        sourceUri: String,
        mimeType: String?,
        filename: String?,
    ): Result<Attachment> =
        withContext(Dispatchers.IO) {
            try {
                val attachmentId = UUID.randomUUID().toString()
                val localPath = attachmentFileStorage.copyToStorage(sourceUri, attachmentId, mimeType)

                val attachment =
                    Attachment(
                        id = attachmentId,
                        noteId = noteId,
                        uri = localPath,
                        mimeType = mimeType,
                        filename = filename,
                    )
                Result.Success(attachment)
            } catch (t: Throwable) {
                Result.Failure(t)
            }
        }
}
