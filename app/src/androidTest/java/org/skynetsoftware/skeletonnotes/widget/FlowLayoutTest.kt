package org.skynetsoftware.skeletonnotes.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [FlowLayout]'s left-to-right wrapping measure/layout: children flow across a row and wrap
 * to the next line when the row runs out of width, honoring child margins.
 */
@RunWith(AndroidJUnit4::class)
class FlowLayoutTest {
    private lateinit var context: Context
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        context = instrumentation.targetContext
    }

    private fun cellParams(
        width: Int,
        height: Int,
        margin: Int = 0,
    ): ViewGroup.MarginLayoutParams =
        ViewGroup.MarginLayoutParams(width, height).apply { setMargins(margin, margin, margin, margin) }

    private fun buildFlow(
        widthPx: Int,
        children: List<ViewGroup.MarginLayoutParams>,
    ): FlowLayout {
        lateinit var flow: FlowLayout
        instrumentation.runOnMainSync {
            flow = FlowLayout(context)
            children.forEach { lp -> flow.addView(View(context), lp) }
            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            flow.measure(widthSpec, heightSpec)
            flow.layout(0, 0, flow.measuredWidth, flow.measuredHeight)
        }
        return flow
    }

    @Test
    fun childrenFitOnOneRow() {
        val flow = buildFlow(250, listOf(cellParams(100, 50), cellParams(100, 50)))

        assertEquals(0, flow.getChildAt(0).top)
        assertEquals(0, flow.getChildAt(1).top)
        assertEquals(100, flow.getChildAt(1).left)
        assertEquals(50, flow.measuredHeight)
    }

    @Test
    fun overflowingChildWrapsToNextRow() {
        val flow = buildFlow(250, listOf(cellParams(100, 50), cellParams(100, 50), cellParams(100, 50)))

        assertEquals(0, flow.getChildAt(0).top)
        assertEquals(0, flow.getChildAt(1).top)
        assertEquals(50, flow.getChildAt(2).top) // wrapped to the second row
        assertEquals(0, flow.getChildAt(2).left)
        assertEquals(100, flow.measuredHeight)
    }

    @Test
    fun marginsPushChildToNextRow() {
        // Two 100px cells with 20px margins each measure 140px wide together per side; at 230px width
        // the second cannot share the row, so it wraps — proving margins are included.
        val flow = buildFlow(230, listOf(cellParams(100, 50, margin = 20), cellParams(100, 50, margin = 20)))

        assertEquals(20, flow.getChildAt(0).left) // left margin honored
        assertTrue("second child should wrap due to margins", flow.getChildAt(1).top > 0)
    }

    @Test
    fun convertsNonMarginLayoutParams() {
        lateinit var flow: FlowLayout
        lateinit var child: View
        instrumentation.runOnMainSync {
            flow = FlowLayout(context)
            child = View(context)
            // A plain (non-Margin) LayoutParams exercises checkLayoutParams(false) + generateLayoutParams(p).
            flow.addView(child, ViewGroup.LayoutParams(100, 50))
        }
        assertTrue(child.layoutParams is ViewGroup.MarginLayoutParams)
    }
}
