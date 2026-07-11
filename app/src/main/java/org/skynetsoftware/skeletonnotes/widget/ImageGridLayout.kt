@file:SuppressLint("UseKtx")

package org.skynetsoftware.skeletonnotes.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import org.skynetsoftware.skeletonnotes.R
import kotlin.math.max

/**
 * A pure layout container that arranges its child views (typically [android.widget.ImageView]s)
 * into Google-Photos / Google-Keep-style **justified rows**, preserving each child's aspect ratio:
 *
 *  - A single visible child fills the full width at its aspect ratio.
 *  - Multiple children flow into rows, each row scaled to a shared height so widths vary by aspect
 *    ratio and the row fills the available width. The last partial row keeps the target height and
 *    is left-aligned.
 *
 * The layout is intentionally dumb: it does **no** image loading and never computes aspect ratios.
 * Each child declares its ratio through [ImageGridLayout.LayoutParams.aspectRatio]; populating the
 * children and loading their bitmaps is the caller's job (mirroring how [FlowLayout] is populated
 * by `NoteAdapter.bindTags`). GONE children are skipped.
 */
class ImageGridLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ViewGroup(context, attrs, defStyleAttr) {
        private val gap = (resources.displayMetrics.density * 3f).toInt()

        /** Target height for a justified row; completed rows are scaled from this to fill the width. */
        val targetRowHeight: Int

        // Per-visible-child geometry, computed in onMeasure and consumed in onLayout.
        private var cellWidths = IntArray(0)
        private var cellHeights = IntArray(0)
        private var cellLefts = IntArray(0)
        private var cellTops = IntArray(0)

        init {
            val a = context.obtainStyledAttributes(attrs, R.styleable.ImageGridLayout)
            targetRowHeight =
                a.getDimensionPixelSize(
                    R.styleable.ImageGridLayout_imageTargetRowHeight,
                    (resources.displayMetrics.density * 120f).toInt(),
                )
            a.recycle()
        }

        private fun visibleChildren(): List<View> =
            (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

        private fun View.aspectRatio(): Float = (layoutParams as? LayoutParams)?.aspectRatio?.takeIf { it > 0f } ?: 1f

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
            val children = visibleChildren()
            val count = children.size

            cellWidths = IntArray(count)
            cellHeights = IntArray(count)
            cellLefts = IntArray(count)
            cellTops = IntArray(count)

            var contentHeight = 0
            if (count == 1) {
                val aspect = children[0].aspectRatio()
                cellWidths[0] = available
                cellHeights[0] = (available / aspect).toInt().coerceAtLeast(1)
                cellLefts[0] = paddingLeft
                cellTops[0] = paddingTop
                contentHeight = cellHeights[0]
            } else if (count > 1) {
                contentHeight = layoutJustifiedRows(children, available)
            }

            for (i in 0 until count) {
                children[i].measure(
                    MeasureSpec.makeMeasureSpec(cellWidths[i], MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cellHeights[i], MeasureSpec.EXACTLY),
                )
            }

            val height = contentHeight + paddingTop + paddingBottom
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
        }

        /**
         * Assigns each visible child's size and position for the justified-rows layout and returns the
         * total content height. Greedily fills rows at [targetRowHeight], then scales each completed row
         * so its children exactly span [available] width; the last, partial row keeps [targetRowHeight].
         */
        private fun layoutJustifiedRows(
            children: List<View>,
            available: Int,
        ): Int {
            val count = children.size
            var y = paddingTop
            var rowStart = 0
            while (rowStart < count) {
                // Grow the row until adding the next child would overflow the available width.
                var aspectSum = 0f
                var end = rowStart
                while (end < count) {
                    val nextAspectSum = aspectSum + children[end].aspectRatio()
                    val gapCount = end - rowStart
                    val rowWidth = targetRowHeight * nextAspectSum + gapCount * gap
                    aspectSum = nextAspectSum
                    end++
                    if (rowWidth >= available) break
                }

                val itemsInRow = end - rowStart
                val totalGap = (itemsInRow - 1) * gap
                val isLastRow = end == count
                val rowFilledWidth = targetRowHeight * aspectSum + totalGap
                // Scale a full row to fill the width; leave the last (partial) row at target height.
                val rowHeight =
                    if (isLastRow && rowFilledWidth < available) {
                        targetRowHeight
                    } else {
                        ((available - totalGap) / aspectSum).toInt().coerceAtLeast(1)
                    }

                var x = paddingLeft
                for (j in rowStart until end) {
                    val w =
                        if (j == end - 1 && !isLastRow) {
                            // Give the row's last cell the remaining width to avoid rounding gaps.
                            max(paddingLeft + available - x, 1)
                        } else {
                            (rowHeight * children[j].aspectRatio()).toInt().coerceAtLeast(1)
                        }
                    cellWidths[j] = w
                    cellHeights[j] = rowHeight
                    cellLefts[j] = x
                    cellTops[j] = y
                    x += w + gap
                }

                y += rowHeight + if (isLastRow) 0 else gap
                rowStart = end
            }
            return y - paddingTop
        }

        override fun onLayout(
            changed: Boolean,
            l: Int,
            t: Int,
            r: Int,
            b: Int,
        ) {
            val children = visibleChildren()
            for (i in children.indices) {
                val left = cellLefts[i]
                val top = cellTops[i]
                children[i].layout(left, top, left + cellWidths[i], top + cellHeights[i])
            }
        }

        override fun generateDefaultLayoutParams(): LayoutParams =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(context, attrs)

        override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams =
            if (p != null) LayoutParams(p) else generateDefaultLayoutParams()

        override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

        /**
         * [ViewGroup.LayoutParams] carrying the child's intrinsic [aspectRatio] (`width / height`),
         * which the layout uses to size the cell. The caller updates it once the ratio is known.
         */
        class LayoutParams : ViewGroup.LayoutParams {
            var aspectRatio: Float = 1f

            constructor(width: Int, height: Int, aspectRatio: Float = 1f) : super(width, height) {
                this.aspectRatio = aspectRatio
            }

            constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

            constructor(source: ViewGroup.LayoutParams) : super(source) {
                (source as? LayoutParams)?.let { aspectRatio = it.aspectRatio }
            }
        }
    }
