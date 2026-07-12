package org.skynetsoftware.skeletonnotes.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudHttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Unit tests for [toSyncErrorReason], which classifies a sync failure for the user-facing message.
 */
class SyncErrorReasonTest {
    @Test
    fun http401MapsToAuthExpired() {
        assertEquals(SyncErrorReason.AUTH_EXPIRED, NextcloudHttpException(401).toSyncErrorReason())
    }

    @Test
    fun http403MapsToAuthExpired() {
        assertEquals(SyncErrorReason.AUTH_EXPIRED, NextcloudHttpException(403).toSyncErrorReason())
    }

    @Test
    fun http500MapsToServerError() {
        assertEquals(SyncErrorReason.SERVER_ERROR, NextcloudHttpException(500).toSyncErrorReason())
    }

    @Test
    fun otherHttpStatusMapsToUnknown() {
        assertEquals(SyncErrorReason.UNKNOWN, NextcloudHttpException(404).toSyncErrorReason())
    }

    @Test
    fun ioExceptionMapsToNoConnection() {
        assertEquals(SyncErrorReason.NO_CONNECTION, IOException("offline").toSyncErrorReason())
        assertEquals(SyncErrorReason.NO_CONNECTION, SocketTimeoutException().toSyncErrorReason())
    }

    @Test
    fun otherThrowableMapsToUnknown() {
        assertEquals(SyncErrorReason.UNKNOWN, IllegalStateException("boom").toSyncErrorReason())
    }
}
