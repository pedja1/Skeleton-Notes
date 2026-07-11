package org.skynetsoftware.skeletonnotes.data.attachment

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Instrumented tests for [AttachmentStorageImpl]. */
@RunWith(AndroidJUnit4::class)
class AttachmentStorageImplInstrumentedTest {
    /** Verifies writes stay contained in the app attachment directory. */
    @Test
    fun writeBytesRejectsPathTraversalAttachmentId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = AttachmentStorageImpl(context)
        val outside = File(context.filesDir, "outside-attachment-test")
        outside.delete()

        val failed =
            runCatching {
                storage.writeStream("../outside-attachment-test", "evil".toByteArray().inputStream())
            }.isFailure

        assertTrue(failed)
        assertFalse(outside.exists())
    }
}
