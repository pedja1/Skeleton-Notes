package org.skynetsoftware.skeletonnotes.note

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan

/**
 * Marks a paragraph as a checklist item and carries its [checked] state. Deliberately NOT a
 * [android.text.style.ParagraphStyle]: an empty item is represented by a zero-length span, and
 * [Layout] attributes a zero-length paragraph span sitting at a line's end boundary to the
 * *preceding* line — a rendering span there would give the previous item a phantom second margin
 * and checkbox. So this base class is what the editor and [MarkdownFormatter] query and persist,
 * while only the [ChecklistSpan] subclass (applied to non-empty paragraphs) takes part in layout;
 * [RichEditText] paints empty items itself via [drawCheckbox] and swaps the classes as paragraphs
 * gain or lose content.
 */
open class ChecklistItemSpan(
    var checked: Boolean,
) {
    protected val density: Float = Resources.getSystem().displayMetrics.density
    protected val boxSize = BOX_SIZE_DP * density
    protected val gap = GAP_DP * density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    /**
     * Draws the checkbox against [baseline] at the margin edge [x]. Drawing is procedural (no
     * drawable/context) so the span can also be created by [MarkdownFormatter] when parsing GFM
     * task-list lines. Toggling [checked] and invalidating the host view redraws it; [RichEditText]
     * handles the tap and the checked-state persistence.
     */
    fun drawCheckbox(
        c: Canvas,
        p: Paint,
        x: Float,
        dir: Int,
        baseline: Float,
    ) {
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
        private const val CORNER_DP = 4f
        private const val STROKE_DP = 2f
        private const val INSET_DP = 2f
    }
}

/**
 * The rendering side of a checklist item: draws a tappable checkbox in the leading margin and adds
 * a little vertical breathing room to its own line (via [LineHeightSpan]) so checklist rows are
 * spaced without affecting plain paragraphs. Applied only to non-empty paragraphs — see
 * [ChecklistItemSpan] for why empty items must not carry a paragraph style.
 */
class ChecklistSpan(
    checked: Boolean,
) : ChecklistItemSpan(checked),
    LeadingMarginSpan,
    LineHeightSpan {
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
        drawCheckbox(c, p, x.toFloat(), dir, baseline.toFloat())
    }

    private companion object {
        private const val ROW_EXTRA_DP = 6f
    }
}
