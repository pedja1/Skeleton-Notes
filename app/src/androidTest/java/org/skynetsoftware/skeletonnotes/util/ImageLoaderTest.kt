package org.skynetsoftware.skeletonnotes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Exercises [ImageLoader]'s file-backed decoding: intrinsic aspect ratio (+ cache), the missing-file
 * fallbacks, and [ImageLoader.load] including its two-pass downsampling. Uses instrumentation because
 * it relies on real `BitmapFactory` decoding of files written to the app cache.
 */
@RunWith(AndroidJUnit4::class)
class ImageLoaderTest {

    private lateinit var context: Context
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(Dispatchers.Main)

    @Before
    fun setUp() {
        context = instrumentation.targetContext
    }

    private fun writeImage(width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(context.cacheDir, "img-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun awaitDrawable(imageView: ImageView, timeoutMs: Long = 3000): Drawable? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var drawable: Drawable? = null
        while (System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync { drawable = imageView.drawable }
            if (drawable != null) return drawable
            Thread.sleep(25)
        }
        return drawable
    }

    @Test
    fun aspectRatioReturnsWidthOverHeight() {
        val path = writeImage(200, 100)
        assertEquals(2f, ImageLoader.aspectRatio(path), 0.01f)
    }

    @Test
    fun cachedAspectRatioIsNullUntilRead() {
        val path = writeImage(120, 60)
        assertNull(ImageLoader.cachedAspectRatio(path))
        ImageLoader.aspectRatio(path)
        assertEquals(2f, ImageLoader.cachedAspectRatio(path)!!, 0.01f)
    }

    @Test
    fun aspectRatioFallsBackToOneForMissingFile() {
        val missing = File(context.cacheDir, "missing-${UUID.randomUUID()}").absolutePath
        assertEquals(1f, ImageLoader.aspectRatio(missing), 0f)
    }

    @Test
    fun loadDecodesAndDownsamplesIntoImageView() {
        val path = writeImage(200, 100)
        lateinit var imageView: ImageView
        instrumentation.runOnMainSync {
            imageView = ImageView(context)
            // Request a target far smaller than the source to exercise inSampleSize downsampling.
            ImageLoader.load(imageView, path, 50, 25, scope)
        }

        val drawable = awaitDrawable(imageView)
        assertNotNull("load should set a bitmap into the ImageView", drawable)
        val bitmap = (drawable as BitmapDrawable).bitmap
        assertTrue("bitmap should be downsampled below source width", bitmap.width < 200)
    }

    @Test
    fun loadMissingFileLeavesNoDrawable() {
        val missing = File(context.cacheDir, "missing-${UUID.randomUUID()}").absolutePath
        lateinit var imageView: ImageView
        instrumentation.runOnMainSync {
            imageView = ImageView(context)
            ImageLoader.load(imageView, missing, 50, 25, scope)
        }

        Thread.sleep(500)
        var drawable: Drawable? = null
        instrumentation.runOnMainSync { drawable = imageView.drawable }
        assertNull(drawable)
    }
}
