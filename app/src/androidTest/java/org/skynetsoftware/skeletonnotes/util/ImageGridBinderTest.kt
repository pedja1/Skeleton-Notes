package org.skynetsoftware.skeletonnotes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.widget.ImageGridLayout
import java.io.File
import java.util.UUID

/**
 * Verifies [bindImages] — the external half of the image grid: creating/reusing [ImageView] cells,
 * feeding aspect ratios into [ImageGridLayout.LayoutParams], hiding surplus cells, and resetting a
 * recycled cell when its path changes. Aspect ratios are pre-warmed into [ImageLoader]'s cache so the
 * synchronous binding effects are deterministic.
 */
@RunWith(AndroidJUnit4::class)
class ImageGridBinderTest {
    private lateinit var context: Context
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(Dispatchers.Main)

    @Before
    fun setUp() {
        context = instrumentation.targetContext
    }

    private fun writeImage(
        width: Int,
        height: Int,
    ): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(context.cacheDir, "img-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // Pre-warm the aspect cache so the binder can set ratios synchronously.
        ImageLoader.aspectRatio(file.absolutePath)
        return file.absolutePath
    }

    private fun aspectOf(child: View): Float = (child.layoutParams as ImageGridLayout.LayoutParams).aspectRatio

    private fun awaitDrawable(
        imageView: ImageView,
        timeoutMs: Long = 3000,
    ): Drawable? {
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
    fun bindEmptyListHidesGrid() {
        lateinit var grid: ImageGridLayout
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)
            grid.bindImages(emptyList(), scope)
        }
        assertEquals(View.GONE, grid.visibility)
    }

    @Test
    fun bindCreatesVisibleCellsWithAspectAndTags() {
        val wide = writeImage(200, 100) // aspect 2.0
        val tall = writeImage(100, 200) // aspect 0.5

        lateinit var grid: ImageGridLayout
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)
            grid.bindImages(listOf(wide, tall), scope)
        }

        assertEquals(View.VISIBLE, grid.visibility)
        assertEquals(2, grid.childCount)
        assertEquals(View.VISIBLE, grid.getChildAt(0).visibility)
        assertEquals(2f, aspectOf(grid.getChildAt(0)), 0.01f)
        assertEquals(0.5f, aspectOf(grid.getChildAt(1)), 0.01f)
        assertEquals(wide, grid.getChildAt(0).getTag(R.id.image_grid_path_tag))
        assertEquals(tall, grid.getChildAt(1).getTag(R.id.image_grid_path_tag))

        assertNotNull(awaitDrawable(grid.getChildAt(0) as ImageView))
    }

    @Test
    fun rebindWithFewerPathsHidesSurplusCell() {
        val a = writeImage(200, 100)
        val b = writeImage(100, 200)

        lateinit var grid: ImageGridLayout
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)
            grid.bindImages(listOf(a, b), scope)
            grid.bindImages(listOf(a), scope)
        }

        assertEquals(2, grid.childCount) // cells reused, not removed
        assertEquals(View.VISIBLE, grid.getChildAt(0).visibility)
        assertEquals(View.GONE, grid.getChildAt(1).visibility)
    }

    @Test
    fun rebindWithDifferentPathResetsRecycledCell() {
        val a = writeImage(200, 100)
        val b = writeImage(100, 200)

        lateinit var grid: ImageGridLayout
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)
            grid.bindImages(listOf(a), scope)
            grid.bindImages(listOf(b), scope)
        }

        assertEquals(b, grid.getChildAt(0).getTag(R.id.image_grid_path_tag))
        assertEquals(0.5f, aspectOf(grid.getChildAt(0)), 0.01f)
        assertNotNull(awaitDrawable(grid.getChildAt(0) as ImageView))
    }
}
