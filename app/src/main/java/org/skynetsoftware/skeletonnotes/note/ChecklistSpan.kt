package org.skynetsoftware.skeletonnotes.note

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.UpdateLayout

/**
 * A paragraph span that renders a tappable checkbox in the leading margin of a checklist item and
 * carries its [checked] state. It also adds a little vertical breathing room to its own line (via
 * [LineHeightSpan]) so checklist rows are spaced without affecting plain paragraphs. Drawing is
 * procedural (no drawable/context) so the span can also be created by [MarkdownFormatter] when parsing
 * GFM task-list lines. Toggling [checked] and invalidating the host view redraws it; [RichEditText]
 * handles the tap and the checked-state persistence.
 *
 * The span must never be zero-length: [Layout] can't render a paragraph span of an empty line, and
 * a zero-length span sitting at a line's end boundary is wrongly attributed to the *preceding*
 * line, giving it a phantom second margin. An empty checklist item therefore keeps a single
 * [EMPTY_ITEM_PLACEHOLDER] (zero-width space) in its paragraph so the span always covers at least
 * one character; [RichEditText] maintains the placeholder as paragraphs gain or lose content and
 * [MarkdownFormatter] strips it at the persistence boundary.
 */
class ChecklistSpan(
    var checked: Boolean,
) : LeadingMarginSpan,
    LineHeightSpan,
    // Marks the span as layout-affecting so DynamicLayout reflows when it is added/removed/re-set.
    // Without this, [RichEditText]'s watcher mutating spans after a text change leaves the layout
    // with mid-edit line metrics, and checklist row spacing visibly jumps while editing.
    UpdateLayout {
    private val density = Resources.getSystem().displayMetrics.density
    private val boxSize = BOX_SIZE_DP * density
    private val gap = GAP_DP * density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    override fun getLeadingMargin(first: Boolean): Int = (boxSize + gap).toInt()

    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        // Add breathing room split above and below the checklist line so consecutive items are spaced.
        val extra = (ROW_EXTRA_DP * density).toInt()
        val half = extra / 2
        fm.top -= half
        fm.ascent -= half
        fm.descent += extra - half
        fm.bottom += extra - half
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        if (!first) return
        // Inset from the margin edge so the box's stroke isn't clipped against the text's left edge.
        val inset = INSET_DP * density
        val left = if (dir >= 0) x + inset else x - boxSize - inset
        // Center on the text's glyphs (baseline + font metrics), not the line box, so it stays aligned
        // with the text regardless of any line spacing added to the paragraph.
        val centerY = baseline + (p.ascent() + p.descent()) / 2f
        val box =
            RectF(
                left,
                centerY - boxSize / 2f,
                left + boxSize,
                centerY + boxSize / 2f,
            )
        val radius = CORNER_DP * density
        paint.color = p.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = STROKE_DP * density
        c.drawRoundRect(box, radius, radius, paint)
        if (checked) {
            val check =
                Path().apply {
                    moveTo(box.left + boxSize * 0.24f, centerY + boxSize * 0.02f)
                    lineTo(box.left + boxSize * 0.42f, centerY + boxSize * 0.22f)
                    lineTo(box.left + boxSize * 0.76f, centerY - boxSize * 0.22f)
                }
            c.drawPath(check, paint)
        }
    }

    companion object {
        const val BOX_SIZE_DP = 18f
        const val GAP_DP = 12f

        /** Zero-width space keeping an empty item's paragraph non-empty so its span can render. */
        const val EMPTY_ITEM_PLACEHOLDER = '\u200B'

        private const val CORNER_DP = 4f
        private const val STROKE_DP = 2f
        private const val INSET_DP = 2f
        private const val ROW_EXTRA_DP = 6f
    }
}
