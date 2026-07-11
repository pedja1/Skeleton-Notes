package org.skynetsoftware.skeletonnotes.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [ImageGridLayout]'s justified-rows measure/layout math and its [ImageGridLayout.LayoutParams]
 * handling. The layout is a pure ViewGroup, so children are plain [ImageView]s carrying an aspect
 * ratio; assertions are made relative to [ImageGridLayout.targetRowHeight] and the density-derived gap
 * so they hold on any device.
 */
@RunWith(AndroidJUnit4::class)
class ImageGridLayoutTest {
    private lateinit var context: Context
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val gap: Int
        get() = (context.resources.displayMetrics.density * 3f).toInt()

    @Before
    fun setUp() {
        context = instrumentation.targetContext
    }

    private fun buildGrid(
        aspects: List<Float>,
        widthPx: Int,
        goneIndices: Set<Int> = emptySet(),
    ): ImageGridLayout {
        lateinit var grid: ImageGridLayout
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)
            aspects.forEachIndexed { i, aspect ->
                val cell = ImageView(context)
                cell.layoutParams =
                    ImageGridLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        aspect,
                    )
                if (i in goneIndices) cell.visibility = View.GONE
                grid.addView(cell)
            }
            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            grid.measure(widthSpec, heightSpec)
            grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        }
        return grid
    }

    @Test
    fun singleImageFillsWidthAtItsAspectRatio() {
        val width = 300
        val grid = buildGrid(listOf(2f), width)
        val child = grid.getChildAt(0)

        assertEquals(width, child.measuredWidth)
        assertEquals(150, child.measuredHeight) // 300 / 2
        assertEquals(150, grid.measuredHeight)
        assertEquals(0, child.left)
        assertEquals(width, child.right)
    }

    @Test
    fun threeImagesFormOneFullWidthScaledRow() {
        val trh = ImageGridLayout(context).targetRowHeight
        val width = trh * 3 // forces all three into a single scaled row
        val grid = buildGrid(listOf(1f, 1f, 1f), width)

        val rowHeight = grid.getChildAt(0).measuredHeight
        // Equal-square inputs scale down to fit three across, so the row is shorter than the target.
        assertTrue(rowHeight in 1 until trh)
        // All on the same row, uniform height, spanning the full width.
        for (i in 0 until 3) {
            assertEquals("child $i top", 0, grid.getChildAt(i).top)
            assertEquals("child $i height", rowHeight, grid.getChildAt(i).measuredHeight)
        }
        // A single row is also the last row, so it is not force-filled — it may fall a couple of
        // pixels short of the exact width from integer rounding (exact fill is asserted on a
        // non-last row in the overflow test).
        assertTrue(grid.getChildAt(2).right in (width - 3)..width)
        assertEquals("grid height must equal the single row height", rowHeight, grid.measuredHeight)
    }

    @Test
    fun overflowingImagesWrapToNextRow() {
        val trh = ImageGridLayout(context).targetRowHeight
        val width = trh * 3
        val grid = buildGrid(List(6) { 1f }, width) // 3 per row => second row exists

        assertEquals(0, grid.getChildAt(0).top)
        assertTrue("child on second row must be below the first", grid.getChildAt(3).top > 0)
        assertTrue(grid.measuredHeight > grid.getChildAt(0).measuredHeight)
        // The first row is a non-last row, so it is force-filled to exactly the available width.
        assertEquals(width, grid.getChildAt(2).right)
    }

    @Test
    fun lastPartialRowKeepsTargetRowHeight() {
        val trh = ImageGridLayout(context).targetRowHeight
        val width = trh * 2 // 2 squares per row => third image is a lone last row
        val grid = buildGrid(listOf(1f, 1f, 1f), width)

        val firstRowHeight = grid.getChildAt(0).measuredHeight
        assertEquals(0, grid.getChildAt(0).top)
        assertEquals(0, grid.getChildAt(1).top)
        // The lone last-row image is NOT stretched: it keeps the target row height.
        assertEquals(trh, grid.getChildAt(2).measuredHeight)
        assertEquals(firstRowHeight + gap, grid.getChildAt(2).top)
    }

    @Test
    fun goneChildrenAreSkipped() {
        val width = 300
        // Only index 0 is visible, so it is treated as a single full-width image.
        val grid = buildGrid(listOf(2f, 1f), width, goneIndices = setOf(1))

        assertEquals(width, grid.getChildAt(0).measuredWidth)
        assertEquals(150, grid.measuredHeight)
        assertEquals(0, grid.getChildAt(1).measuredWidth) // GONE child never measured
    }

    @Test
    fun emptyLayoutHasZeroContentHeight() {
        val grid = buildGrid(emptyList(), 300)
        assertEquals(0, grid.measuredHeight)
    }

    @Test
    fun layoutParamsAreDefaultedAndConverted() {
        lateinit var grid: ImageGridLayout
        lateinit var noLpChild: ImageView
        lateinit var plainLpChild: ImageView
        instrumentation.runOnMainSync {
            grid = ImageGridLayout(context)

            noLpChild = ImageView(context)
            grid.addView(noLpChild) // uses generateDefaultLayoutParams

            plainLpChild = ImageView(context)
            // A non-ImageGridLayout LayoutParams triggers checkLayoutParams + generateLayoutParams(p).
            grid.addView(plainLpChild, ViewGroup.LayoutParams(0, 0))
        }

        // addView(noLpChild) exercises generateDefaultLayoutParams; addView(plainLpChild, plainLp)
        // exercises checkLayoutParams (false) + generateLayoutParams(p) conversion.
        assertTrue(noLpChild.layoutParams is ImageGridLayout.LayoutParams)
        assertTrue(plainLpChild.layoutParams is ImageGridLayout.LayoutParams)
        assertEquals(1f, (noLpChild.layoutParams as ImageGridLayout.LayoutParams).aspectRatio, 0f)
        assertEquals(2, grid.childCount)
    }
}
