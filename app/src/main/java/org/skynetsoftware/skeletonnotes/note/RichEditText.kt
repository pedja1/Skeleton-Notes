package org.skynetsoftware.skeletonnotes.note

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.EditText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * An [EditText] that owns the note editor's WYSIWYG span model so the toolbar only has to
 * call toggle methods and reflect [FormattingState]. It fixes two long-standing pain points of the
 * old Activity-driven toolbar:
 *
 * - **Compose mode:** toggling an inline style with no selection flips a *typing attribute* so the
 *   next characters typed carry the style (turn bold on, keep typing) instead of doing nothing.
 * - **Active state:** [onFormattingStateChanged] reports what is active at the cursor/selection so
 *   the toolbar buttons can show a selected state.
 *
 * Formatting is represented with the same spans the rest of the app uses (see [MarkdownFormatter]):
 * bold/italic as [StyleSpan], strikethrough/underline as [StrikethroughSpan]/[UnderlineSpan], H1/H2
 * as a paragraph-wide [RelativeSizeSpan] plus bold [StyleSpan], and checklist items as a
 * [ChecklistSpan].
 */
@Suppress("TooManyFunctions") // A cohesive rich-text widget; its span helpers read best kept together.
class RichEditText
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.editTextStyle,
    ) : EditText(context, attrs, defStyleAttr) {
        /** Snapshot of the formatting active at the current cursor/selection, for the toolbar. */
        data class FormattingState(
            val bold: Boolean = false,
            val italic: Boolean = false,
            val strikethrough: Boolean = false,
            val underline: Boolean = false,
            val headingLevel: Int? = null,
            val checklist: Boolean = false,
        )

        /** Invoked whenever the active formatting changes (selection moved, style toggled, edit). */
        var onFormattingStateChanged: ((FormattingState) -> Unit)? = null

        /** Inline styles pending for the next typed characters (compose mode / typing attributes). */
        private val pendingInline = linkedSetOf<InlineStyle>()

        /** Guards re-entrancy while we mutate spans/text ourselves so watchers/callbacks stay quiet. */
        private var applyingInternally = false

        /** False until construction finishes, so super-constructor callbacks are ignored safely. */
        private var ready = false

        // Insertion bookkeeping captured in onTextChanged and consumed in afterTextChanged.
        private var insertStart = -1
        private var insertCount = 0

        // Start offset of a line armed to become a checklist item when typed into (Enter continuation).
        private var pendingChecklistLineStart = -1

        private val checkboxRegionWidthPx =
            (ChecklistSpan.BOX_SIZE_DP + ChecklistSpan.GAP_DP) * resources.displayMetrics.density
        private var armedChecklistSpan: ChecklistSpan? = null

        init {
            addTextChangedListener(ComposeWatcher())
            ready = true
        }

        /**
         * Replaces the content without triggering compose-mode styling, for loading a saved note.
         * Preserves the [Spanned] passed in (spans from [MarkdownFormatter.fromMarkdown]).
         */
        fun setContentSilently(content: CharSequence) {
            applyingInternally = true
            setText(content)
            applyingInternally = false
            post { emitState() }
        }

        fun toggleBold() = toggleInline(InlineStyle.BOLD)

        fun toggleItalic() = toggleInline(InlineStyle.ITALIC)

        fun toggleStrikethrough() = toggleInline(InlineStyle.STRIKETHROUGH)

        fun toggleUnderline() = toggleInline(InlineStyle.UNDERLINE)

        /** Applies heading [level] (1 or 2) or reverts to a plain paragraph when [level] is null. */
        fun applyHeading(level: Int?) {
            val editable = text ?: return
            val (pStart, pEnd) = paragraphRange(selectionStart, selectionEnd)
            if (pStart >= pEnd) {
                emitState()
                return
            }
            applyingInternally = true
            editable.getSpans(pStart, pEnd, RelativeSizeSpan::class.java).forEach { editable.removeSpan(it) }
            // Remove only the heading's paragraph-wide bold, preserving any inline bold inside it.
            editable
                .getSpans(pStart, pEnd, StyleSpan::class.java)
                .filter {
                    it.style == Typeface.BOLD &&
                        editable.getSpanStart(it) <= pStart &&
                        editable.getSpanEnd(it) >= pEnd
                }.forEach { editable.removeSpan(it) }
            if (level != null) {
                val scale = if (level == 1) MarkdownFormatter.H1_SCALE else MarkdownFormatter.H2_SCALE
                editable.setSpan(RelativeSizeSpan(scale), pStart, pEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                editable.setSpan(StyleSpan(Typeface.BOLD), pStart, pEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
            }
            applyingInternally = false
            emitState()
        }

        /** Toggles the current paragraph(s) between a checklist item and a plain paragraph. */
        fun toggleChecklist() {
            val editable = text ?: return
            val (pStart, pEnd) = paragraphRange(selectionStart, selectionEnd)
            applyingInternally = true
            val existing = checklistSpanStartingIn(editable, pStart, pEnd)
            if (existing != null) {
                editable.removeSpan(existing)
            } else {
                editable.setSpan(
                    ChecklistSpan(false),
                    pStart,
                    paragraphSpanEnd(editable, pEnd),
                    Spannable.SPAN_PARAGRAPH,
                )
            }
            applyingInternally = false
            emitState()
            invalidate()
        }

        override fun onSelectionChanged(
            selStart: Int,
            selEnd: Int,
        ) {
            super.onSelectionChanged(selStart, selEnd)
            if (!ready || applyingInternally) return
            // A collapsed cursor inherits the styles around it as the next typing attributes.
            if (selStart == selEnd) {
                pendingInline.clear()
                pendingInline.addAll(inlineStylesAt(selStart))
            }
            emitState()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val span = checklistSpanAtTouch(event.x, event.y)
                    if (span != null) {
                        armedChecklistSpan = span
                        return true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val span = armedChecklistSpan
                    if (span != null) {
                        armedChecklistSpan = null
                        span.checked = !span.checked
                        redrawSpan(span)
                        emitState()
                        return true
                    }
                }

                MotionEvent.ACTION_CANCEL -> armedChecklistSpan = null
            }
            return super.onTouchEvent(event)
        }

        /**
         * Forces the checkbox to repaint after its [ChecklistSpan.checked] state is mutated in place.
         * A bare [invalidate] reuses the cached text render node; re-setting the span at its current
         * range notifies the text's span watchers so the leading margin is redrawn.
         */
        private fun redrawSpan(span: Any) {
            val editable = text ?: return
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start >= 0) editable.setSpan(span, start, end, editable.getSpanFlags(span))
        }

        private fun toggleInline(style: InlineStyle) {
            val editable = text ?: return
            val lo = min(selectionStart, selectionEnd).coerceAtLeast(0)
            val hi = max(selectionStart, selectionEnd).coerceAtLeast(0)
            if (lo == hi) {
                if (style in pendingInline) pendingInline.remove(style) else pendingInline.add(style)
            } else {
                applyingInternally = true
                if (isStyleAppliedThroughout(editable, style, lo, hi)) {
                    removeInlineStyle(editable, style, lo, hi)
                } else {
                    applyInlineStyle(editable, style, lo, hi)
                }
                applyingInternally = false
            }
            emitState()
        }

        /** Applies [style] over [from]..[to], merging with any overlapping/adjacent same-style run. */
        private fun applyInlineStyle(
            editable: Editable,
            style: InlineStyle,
            from: Int,
            to: Int,
        ) {
            if (from >= to) return
            var start = from
            var end = to
            val lo = (from - 1).coerceAtLeast(0)
            val hi = (to + 1).coerceAtMost(editable.length)
            editable.getSpans(lo, hi, style.spanClass).forEach { span ->
                if (!style.matches(span) || isHeadingBold(style, span, editable) || editable.isComposingSpan(span)) {
                    return@forEach
                }
                val ss = editable.getSpanStart(span)
                val se = editable.getSpanEnd(span)
                if (se >= from && ss <= to) {
                    start = min(start, ss)
                    end = max(end, se)
                    editable.removeSpan(span)
                }
            }
            editable.setSpan(style.newSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        /** Removes [style] within [from]..[to], re-adding the portions of spans that fall outside. */
        private fun removeInlineStyle(
            editable: Editable,
            style: InlineStyle,
            from: Int,
            to: Int,
        ) {
            editable.getSpans(from, to, style.spanClass).forEach { span ->
                if (!style.matches(span) || isHeadingBold(style, span, editable) || editable.isComposingSpan(span)) {
                    return@forEach
                }
                val ss = editable.getSpanStart(span)
                val se = editable.getSpanEnd(span)
                editable.removeSpan(span)
                if (ss < from) editable.setSpan(style.newSpan(), ss, from, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (se > to) editable.setSpan(style.newSpan(), to, se, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun isStyleAppliedThroughout(
            editable: Editable,
            style: InlineStyle,
            from: Int,
            to: Int,
        ): Boolean {
            if (from >= to) return false
            var index = from
            while (index < to) {
                val next = editable.nextSpanTransition(index, to, style.spanClass)
                val covered =
                    editable.getSpans(index, next, style.spanClass).any {
                        style.matches(it) &&
                            !isHeadingBold(style, it, editable) &&
                            !editable.isComposingSpan(it) &&
                            editable.getSpanStart(it) <= index &&
                            editable.getSpanEnd(it) >= next
                    }
                if (!covered) return false
                index = next
            }
            return true
        }

        private fun inlineStylesAt(pos: Int): Set<InlineStyle> {
            val editable = text ?: return emptySet()
            if (editable.isEmpty()) return emptySet()
            val at = (pos - 1).coerceIn(0, editable.length - 1)
            val result = linkedSetOf<InlineStyle>()
            for (style in InlineStyle.entries) {
                val present =
                    editable.getSpans(at, at + 1, style.spanClass).any {
                        style.matches(it) && !isHeadingBold(style, it, editable) && !editable.isComposingSpan(it)
                    }
                if (present) result.add(style)
            }
            return result
        }

        /**
         * True when [span] is the whole-paragraph bold that renders a heading (as opposed to an inline
         * bold the user applied). Heading bold must be ignored by the inline bold toggle/detection so
         * bolding a word inside a heading doesn't strip the heading, and vice versa.
         */
        private fun isHeadingBold(
            style: InlineStyle,
            span: Any,
            editable: Editable,
        ): Boolean {
            if (style != InlineStyle.BOLD || span !is StyleSpan || span.style != Typeface.BOLD) return false
            val ss = editable.getSpanStart(span)
            val se = editable.getSpanEnd(span)
            val (pStart, pEnd) = paragraphRange(ss, ss)
            return ss <= pStart &&
                se >= pEnd &&
                editable.getSpans(pStart, max(pStart, pEnd), RelativeSizeSpan::class.java).isNotEmpty()
        }

        /**
         * True for a transient span the IME places on the composing (being-typed) region — e.g. the
         * spell-check underline. These must be ignored so they aren't mistaken for, or merged with,
         * user formatting.
         */
        private fun Editable.isComposingSpan(span: Any): Boolean = (getSpanFlags(span) and Spanned.SPAN_COMPOSING) != 0

        private fun headingLevelAt(pos: Int): Int? {
            val editable = text ?: return null
            val (pStart, pEnd) = paragraphRange(pos, pos)
            val span =
                editable
                    .getSpans(pStart, max(pStart, pEnd), RelativeSizeSpan::class.java)
                    .firstOrNull() ?: return null
            return when {
                abs(span.sizeChange - MarkdownFormatter.H1_SCALE) < SCALE_TOLERANCE -> 1
                abs(span.sizeChange - MarkdownFormatter.H2_SCALE) < SCALE_TOLERANCE -> 2
                else -> null
            }
        }

        private fun paragraphRange(
            selStart: Int,
            selEnd: Int,
        ): Pair<Int, Int> {
            val s = text?.toString() ?: return 0 to 0
            val start = selStart.coerceIn(0, s.length)
            val end = selEnd.coerceIn(0, s.length)
            val pStart =
                s
                    .lastIndexOf('\n', (start - 1).coerceAtLeast(0))
                    .let { if (it < 0) 0 else it + 1 }
            val pEnd = s.indexOf('\n', end).let { if (it < 0) s.length else it }
            return pStart to pEnd
        }

        /** Paragraph spans (SPAN_PARAGRAPH) must end at a '\n' boundary or the buffer end. */
        private fun paragraphSpanEnd(
            text: CharSequence,
            pEnd: Int,
        ): Int = if (pEnd < text.length && text[pEnd] == '\n') pEnd + 1 else pEnd

        /**
         * Returns the checklist item whose checkbox was tapped, or null. Hit-tests by walking the
         * checklist spans and matching the tap against each item's first-line bounds and the leading
         * checkbox column, which is more reliable than a point span query at a paragraph boundary.
         */
        private fun checklistSpanAtTouch(
            x: Float,
            y: Float,
        ): ChecklistSpan? {
            if (x > totalPaddingLeft + checkboxRegionWidthPx) return null
            val editable = text ?: return null
            val currentLayout = layout ?: return null
            val verticalInLayout = y - totalPaddingTop + scrollY
            return editable.getSpans(0, editable.length, ChecklistSpan::class.java).firstOrNull { span ->
                val line = currentLayout.getLineForOffset(editable.getSpanStart(span))
                verticalInLayout >= currentLayout.getLineTop(line) &&
                    verticalInLayout <= currentLayout.getLineBottom(line)
            }
        }

        private fun emitState() {
            val listener = onFormattingStateChanged ?: return
            val editable = text
            val start = selectionStart
            val end = selectionEnd
            if (editable == null || start < 0) {
                listener(FormattingState())
                return
            }
            val lo = min(start, end)
            val hi = max(start, end)

            fun active(style: InlineStyle) =
                if (lo == hi) style in pendingInline else isStyleAppliedThroughout(editable, style, lo, hi)
            val (pStart, pEnd) = paragraphRange(start, start)
            listener(
                FormattingState(
                    bold = active(InlineStyle.BOLD),
                    italic = active(InlineStyle.ITALIC),
                    strikethrough = active(InlineStyle.STRIKETHROUGH),
                    underline = active(InlineStyle.UNDERLINE),
                    headingLevel = headingLevelAt(start),
                    checklist = checklistSpanStartingIn(editable, pStart, pEnd) != null,
                ),
            )
        }

        /**
         * Drives compose-mode styling of newly typed text and the checklist editing behaviours
         * (Enter continues a list, Enter on an empty item ends it), then reconciles checklist span
         * bounds so each stays confined to its own paragraph.
         */
        private inner class ComposeWatcher : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                if (!ready || applyingInternally) return
                insertStart = start
                insertCount = count
            }

            override fun afterTextChanged(s: Editable?) {
                if (!ready || applyingInternally || s == null) return
                val start = insertStart
                val count = insertCount
                insertStart = -1
                insertCount = 0
                if (start < 0 || count <= 0) return
                applyingInternally = true
                try {
                    val end = (start + count).coerceAtMost(s.length)
                    for (style in pendingInline) applyInlineStyle(s, style, start, end)
                    if (count == 1 && start < s.length && s[start] == '\n') {
                        handleChecklistNewline(s, start)
                    } else {
                        continuePendingChecklist(s, start)
                    }
                    normalizeChecklistSpans(s)
                } finally {
                    applyingInternally = false
                }
                emitState()
            }
        }

        /**
         * Handles a just-typed newline at [newlineIndex] for checklist continuation. If the line that
         * ended was a checklist item with content, the new line immediately becomes a checklist item
         * (so its checkbox shows at once) and is also armed so the span is re-applied once typed into,
         * in case the empty-paragraph span is dropped meanwhile. Enter on an empty item ends the list.
         */
        private fun handleChecklistNewline(
            editable: Editable,
            newlineIndex: Int,
        ) {
            val content = editable.toString()
            val prevLineStart =
                content
                    .lastIndexOf('\n', (newlineIndex - 1).coerceAtLeast(0))
                    .let { if (it < 0) 0 else it + 1 }
            val prevEmpty = newlineIndex == prevLineStart
            // Only a span that starts on the previous line counts; the line before it can bleed a
            // SPAN_PARAGRAPH across the boundary, and removing that would wrongly clear another item.
            val prevChecklist =
                editable
                    .getSpans(
                        prevLineStart,
                        maxOf((prevLineStart + 1).coerceAtMost(editable.length), newlineIndex),
                        ChecklistSpan::class.java,
                    ).firstOrNull { editable.getSpanStart(it) >= prevLineStart }
            val inChecklist = prevChecklist != null || pendingChecklistLineStart == prevLineStart
            pendingChecklistLineStart = -1
            if (!inChecklist) return
            if (prevEmpty) {
                prevChecklist?.let { editable.removeSpan(it) }
                return
            }
            val newLineStart = newlineIndex + 1
            pendingChecklistLineStart = newLineStart
            // Apply immediately only when the new line already has content (e.g. Enter split a line).
            // An empty new line can't render a checkbox and would serialize a stray "- [ ] ", so it is
            // left armed and turned into a checklist item on the first keystroke instead.
            val (nStart, nEnd) = paragraphRange(newLineStart, newLineStart)
            if (nEnd > nStart) applyChecklistToParagraphAt(editable, newLineStart)
        }

        /** Re-applies the checklist span to the line armed by [handleChecklistNewline] once it gets content. */
        private fun continuePendingChecklist(
            editable: Editable,
            at: Int,
        ) {
            val armed = pendingChecklistLineStart
            pendingChecklistLineStart = -1
            if (armed >= 0 && paragraphRange(at, at).first == armed) applyChecklistToParagraphAt(editable, at)
        }

        /** Adds a checklist span to the paragraph containing [at] unless it already starts one. */
        private fun applyChecklistToParagraphAt(
            editable: Editable,
            at: Int,
        ) {
            val (pStart, pEnd) = paragraphRange(at, at)
            if (checklistSpanStartingIn(editable, pStart, pEnd) == null) {
                editable.setSpan(
                    ChecklistSpan(false),
                    pStart,
                    paragraphSpanEnd(editable, pEnd),
                    Spannable.SPAN_PARAGRAPH,
                )
            }
        }

        /**
         * The checklist span that starts in the paragraph [pStart]..[pEnd], or null. Filtering by start
         * offset ignores a previous paragraph's [ChecklistSpan] that a boundary query can return.
         */
        private fun checklistSpanStartingIn(
            editable: Editable,
            pStart: Int,
            pEnd: Int,
        ): ChecklistSpan? =
            editable
                .getSpans(
                    pStart,
                    maxOf((pStart + 1).coerceAtMost(editable.length), pEnd),
                    ChecklistSpan::class.java,
                ).firstOrNull { editable.getSpanStart(it) >= pStart }

        /** Snaps every checklist span to exactly one paragraph and drops duplicates/empties. */
        private fun normalizeChecklistSpans(editable: Editable) {
            val seenParagraphStarts = HashSet<Int>()
            editable.getSpans(0, editable.length, ChecklistSpan::class.java).forEach { span ->
                val spanStart = editable.getSpanStart(span)
                if (spanStart >= editable.length && editable.isNotEmpty()) {
                    editable.removeSpan(span)
                    return@forEach
                }
                val (pStart, pEnd) = paragraphRange(spanStart, spanStart)
                val paraEnd = paragraphSpanEnd(editable, pEnd)
                if (!seenParagraphStarts.add(pStart)) {
                    editable.removeSpan(span)
                    return@forEach
                }
                if (editable.getSpanStart(span) != pStart || editable.getSpanEnd(span) != paraEnd) {
                    val checked = span.checked
                    editable.removeSpan(span)
                    editable.setSpan(ChecklistSpan(checked), pStart, paraEnd, Spannable.SPAN_PARAGRAPH)
                }
            }
        }

        /** The inline styles the toolbar can toggle, each paired with its span representation. */
        private enum class InlineStyle {
            BOLD,
            ITALIC,
            STRIKETHROUGH,
            UNDERLINE,
            ;

            val spanClass: Class<*>
                get() =
                    when (this) {
                        BOLD, ITALIC -> StyleSpan::class.java
                        STRIKETHROUGH -> StrikethroughSpan::class.java
                        UNDERLINE -> UnderlineSpan::class.java
                    }

            fun newSpan(): Any =
                when (this) {
                    BOLD -> StyleSpan(Typeface.BOLD)
                    ITALIC -> StyleSpan(Typeface.ITALIC)
                    STRIKETHROUGH -> StrikethroughSpan()
                    UNDERLINE -> UnderlineSpan()
                }

            fun matches(span: Any): Boolean =
                when (this) {
                    BOLD -> span is StyleSpan && (span.style and Typeface.BOLD) != 0
                    ITALIC -> span is StyleSpan && (span.style and Typeface.ITALIC) != 0
                    STRIKETHROUGH -> span is StrikethroughSpan
                    UNDERLINE -> span is UnderlineSpan
                }
        }

        private companion object {
            private const val SCALE_TOLERANCE = 0.05f
        }
    }
