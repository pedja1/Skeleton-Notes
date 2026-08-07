package org.skynetsoftware.skeletonnotes.note

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [ChecklistSpan]'s procedural rendering and paragraph metrics: it reserves a leading
 * checkbox column, adds vertical breathing room to its own line, and draws a rounded box (plus a
 * checkmark when checked) only on the first line of the paragraph. Drawing is asserted by rendering
 * into an off-screen [Bitmap] and counting the pixels the span painted, so the checks hold on any
 * device density.
 */
@RunWith(AndroidJUnit4::class)
class ChecklistSpanInstrumentedTest {
    private val density = Resources.getSystem().displayMetrics.density

    /** A text paint with a non-zero size so the span has real ascent/descent to center against. */
    private fun textPaint(): TextPaint =
        TextPaint().apply {
            color = Color.BLACK
            textSize = TEXT_SIZE
        }

    /** A throwaway [StaticLayout] to satisfy the non-null layout parameter; the span never reads it. */
    private fun dummyLayout(paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain("x", 0, 1, paint, BITMAP_SIZE).build()

    /** Renders the span's leading margin into a blank bitmap and returns the count of painted pixels. */
    private fun countDrawnPixels(
        checked: Boolean,
        first: Boolean = true,
        dir: Int = 1,
        x: Int = 0,
    ): Int {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = textPaint()
        ChecklistSpan(checked).drawLeadingMargin(
            canvas,
            paint,
            x,
            dir,
            0,
            BASELINE,
            BITMAP_SIZE,
            "x",
            0,
            1,
            first,
            dummyLayout(paint),
        )
        var painted = 0
        for (px in 0 until BITMAP_SIZE) {
            for (py in 0 until BITMAP_SIZE) {
                if (bitmap.getPixel(px, py) != Color.TRANSPARENT) painted++
            }
        }
        bitmap.recycle()
        return painted
    }

    @Test
    fun checkedStateIsMutable() {
        val span = ChecklistSpan(false)
        assertFalse(span.checked)
        span.checked = true
        assertTrue(span.checked)
    }

    @Test
    fun leadingMarginReservesSpaceForCheckbox() {
        val span = ChecklistSpan(false)
        val expected = ((ChecklistSpan.BOX_SIZE_DP + ChecklistSpan.GAP_DP) * density).toInt()
        assertEquals(expected, span.getLeadingMargin(true))
        assertEquals(expected, span.getLeadingMargin(false))
    }

    @Test
    fun chooseHeightAddsVerticalBreathingRoom() {
        val fm =
            Paint.FontMetricsInt().apply {
                top = -100
                ascent = -80
                descent = 20
                bottom = 40
            }
        val originalTop = fm.top
        val originalBottom = fm.bottom
        ChecklistSpan(false).chooseHeight("x", 0, 1, 0, 0, fm)
        assertTrue("top should move further up", fm.top < originalTop)
        assertTrue("bottom should move further down", fm.bottom > originalBottom)
    }

    @Test
    fun drawLeadingMarginRendersUncheckedBox() {
        assertTrue("an unchecked box should paint the outline", countDrawnPixels(checked = false) > 0)
    }

    @Test
    fun drawLeadingMarginRendersCheckmarkWhenChecked() {
        val unchecked = countDrawnPixels(checked = false)
        val checked = countDrawnPixels(checked = true)
        assertTrue("a checked box paints the extra checkmark", checked > unchecked)
    }

    @Test
    fun drawLeadingMarginSkipsContinuationLines() {
        assertEquals(
            "only the first line of a paragraph gets a checkbox",
            0,
            countDrawnPixels(checked = true, first = false),
        )
    }

    @Test
    fun drawLeadingMarginRendersRightToLeft() {
        // dir < 0 places the box to the left of x, so anchor x at the right edge to keep it on-canvas.
        assertTrue(
            "the RTL box should paint the outline",
            countDrawnPixels(checked = false, dir = -1, x = BITMAP_SIZE) > 0,
        )
    }

    private companion object {
        private const val BITMAP_SIZE = 200
        private const val BASELINE = 100
        private const val TEXT_SIZE = 40f
    }
}
