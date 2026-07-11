package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage

class DeleteAttachmentLocalUseCase(
    private val attachmentFileStorage: AttachmentFileStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(attachmentId: String) =
        withContext(dispatcher) {
            attachmentFileStorage.deleteFile(attachmentId)
        }
}
