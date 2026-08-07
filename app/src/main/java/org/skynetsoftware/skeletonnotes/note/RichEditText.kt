package org.skynetsoftware.skeletonnotes.note

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.util.Patterns
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
 * [ChecklistItemSpan] (the rendering [ChecklistSpan] subclass on non-empty paragraphs).
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

        /**
         * Invoked when the cursor moves onto or off a link. When [url] is non-null the cursor sits
         * on a clickable link ([URLSpan] or a raw URL pattern); [x] and [y] are the screen-relative
         * coordinates of the cursor so the caller can anchor a popup. When [url] is null the cursor
         * moved away from any link and any popup should be dismissed.
         */
        var onLinkContextChanged: ((url: String?, x: Float, y: Float) -> Unit)? = null

        /** Inline styles pending for the next typed characters (compose mode / typing attributes). */
        private val pendingInline = linkedSetOf<InlineStyle>()

        /** Guards re-entrancy while we mutate spans/text ourselves so watchers/callbacks stay quiet. */
        private var applyingInternally = false

        /** False until construction finishes, so super-constructor callbacks are ignored safely. */
        private var ready = false

        // Change bookkeeping captured in beforeTextChanged/onTextChanged and consumed in
        // afterTextChanged. Bounds are accumulated (min/max) so a change delivered in several
        // onTextChanged segments is still processed as one region.
        private var changedStart = -1
        private var changedEnd = -1
        private var insertedCount = 0
        private var removedCount = 0

        /**
         * The checklist items present just before a text change. Editable implementations differ in
         * how they move (or drop) a zero-length paragraph span when text is inserted at its position,
         * so [reanchorChecklistItems] uses this snapshot to restore any item the change knocked loose.
         */
        private var checklistSnapshot: List<ChecklistItemSnapshot> = emptyList()

        private data class ChecklistItemSnapshot(
            val paragraphStart: Int,
            val checked: Boolean,
            val span: ChecklistItemSpan,
        )

        private val checkboxRegionWidthPx =
            (ChecklistItemSpan.BOX_SIZE_DP + ChecklistItemSpan.GAP_DP) * resources.displayMetrics.density
        private var armedChecklistSpan: ChecklistItemSpan? = null

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
            text?.let { applyAutoDetectedLinks(it as Editable, 0, it.length) }
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
                setChecklistSpan(editable, pStart, paragraphSpanEnd(editable, pEnd), checked = false)
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
            if (selStart == selEnd) {
                pendingInline.clear()
                pendingInline.addAll(inlineStylesAt(selStart))
            }
            emitState()
            notifyLinkContext(selStart, selEnd)
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
         * Paints the checkbox of every empty checklist item. The framework can't: paragraph spans of
         * a zero-length line are invisible to [android.text.Layout.getParagraphSpans], and on an
         * empty buffer the hint layout is drawn instead of the text layout. The span still carries
         * the item (state, serialization), so only its checkbox needs drawing here.
         */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val editable = text ?: return
            val currentLayout = layout ?: return
            editable.getSpans(0, editable.length, ChecklistItemSpan::class.java).forEach { span ->
                val start = editable.getSpanStart(span)
                if (start != editable.getSpanEnd(span)) return@forEach
                val line = currentLayout.getLineForOffset(start)
                val dir = currentLayout.getParagraphDirection(line)
                val x = if (dir >= 0) totalPaddingLeft else width - totalPaddingRight
                paint.color = currentTextColor
                span.drawCheckbox(
                    canvas,
                    paint,
                    x.toFloat(),
                    dir,
                    (currentLayout.getLineBaseline(line) + totalPaddingTop).toFloat(),
                )
            }
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
            // Scans the CharSequence directly: this runs several times per keystroke, and a
            // toString() here would copy the whole document each time.
            val s = text ?: return 0 to 0
            val start = selStart.coerceIn(0, s.length)
            val end = selEnd.coerceIn(0, s.length)
            val pStart =
                TextUtils
                    .lastIndexOf(s, '\n', (start - 1).coerceAtLeast(0))
                    .let { if (it < 0) 0 else it + 1 }
            val pEnd = TextUtils.indexOf(s, '\n', end).let { if (it < 0) s.length else it }
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
        ): ChecklistItemSpan? {
            if (x > totalPaddingLeft + checkboxRegionWidthPx) return null
            val editable = text ?: return null
            val currentLayout = layout ?: return null
            val verticalInLayout = y - totalPaddingTop + scrollY
            return editable.getSpans(0, editable.length, ChecklistItemSpan::class.java).firstOrNull { span ->
                val line = currentLayout.getLineForOffset(editable.getSpanStart(span))
                verticalInLayout >= currentLayout.getLineTop(line) &&
                    verticalInLayout <= currentLayout.getLineBottom(line)
            }
        }

        /**
         * Scans the text around the changed region ([changeStart]..[changeEnd]) for raw URL patterns
         * and applies an [AutoDetectedUrlSpan] to each that is not already covered by a Markdown
         * [URLSpan]. Existing [AutoDetectedUrlSpan] instances overlapping the region are removed first
         * so the span set stays in sync with the current text.
         */
        private fun applyAutoDetectedLinks(
            editable: Editable,
            changeStart: Int,
            changeEnd: Int,
        ) {
            if (changeEnd <= changeStart || changeStart > editable.length) return
            val searchStart = (changeStart - URL_CONTEXT_WINDOW).coerceAtLeast(0)
            val searchEnd = (changeEnd + URL_CONTEXT_WINDOW).coerceAtMost(editable.length)
            if (searchEnd <= searchStart) return

            editable
                .getSpans(searchStart, searchEnd, AutoDetectedUrlSpan::class.java)
                .filter { editable.getSpanEnd(it) >= searchStart && editable.getSpanStart(it) <= searchEnd }
                .forEach { editable.removeSpan(it) }

            applyingInternally = true
            try {
                applyUrlMatches(editable, searchStart, searchEnd)
            } finally {
                applyingInternally = false
            }
        }

        /** Walks [Patterns.WEB_URL] matches in [[searchStart]..[searchEnd]) and applies [AutoDetectedUrlSpan]. */
        private fun applyUrlMatches(
            editable: Editable,
            searchStart: Int,
            searchEnd: Int,
        ) {
            val matcher = Patterns.WEB_URL.matcher(editable)
            // Confine the scan to the search window: without a region the regex walks the whole
            // document from position 0 on every keystroke. The region end stays at the document
            // end (with an early break below) so a URL crossing searchEnd is skipped whole
            // instead of being matched truncated at the region boundary.
            matcher.region(searchStart, editable.length)
            while (matcher.find()) {
                val start = matcher.start()
                if (start >= searchEnd) break
                val end = matcher.end()
                if (end > searchEnd) continue
                if (hasMarkdownUrlSpan(editable, start, end)) continue
                val raw = matcher.group()
                val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                editable.setSpan(AutoDetectedUrlSpan(url), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        /** True when [start]..[end] is already covered by a Markdown [URLSpan] (not auto-detected). */
        private fun hasMarkdownUrlSpan(
            editable: Editable,
            start: Int,
            end: Int,
        ) = editable
            .getSpans(start, end, URLSpan::class.java)
            .any { it !is AutoDetectedUrlSpan }

        /**
         * Computes the screen-relative coordinates of the cursor at [offset] and invokes
         * [onLinkContextChanged] with the result of [findUrlAtOffset], or null if no link is found
         * or a range is selected.
         */
        private fun notifyLinkContext(
            selStart: Int,
            selEnd: Int,
        ) {
            val listener = onLinkContextChanged ?: return
            if (selStart != selEnd) {
                listener(null, 0f, 0f)
                return
            }
            val url = findUrlAtOffset(selStart)
            if (url == null) {
                listener(null, 0f, 0f)
                return
            }
            val currentLayout = layout ?: return
            val line = currentLayout.getLineForOffset(selStart)
            val x = currentLayout.getPrimaryHorizontal(selStart) + totalPaddingLeft - scrollX
            val y = currentLayout.getLineBottom(line).toFloat() + totalPaddingTop - scrollY
            val screenCoords = IntArray(2)
            getLocationOnScreen(screenCoords)
            listener(url, screenCoords[0] + x, screenCoords[1] + y)
        }

        /**
         * Returns the URL at the given character [offset], or null. Checks for a [URLSpan] first
         * (markdown links), then falls back to a raw URL match via [Patterns.WEB_URL].
         */
        private fun findUrlAtOffset(offset: Int): String? {
            val editable = text ?: return null
            if (offset < 0 || offset > editable.length) return null

            val urlSpan = editable.getSpans(offset, offset, URLSpan::class.java).firstOrNull()
            if (urlSpan != null) return urlSpan.url

            // Match directly on the CharSequence within a window around the cursor: a toString()
            // plus full-document scan here would run on every cursor move in a large note.
            val windowStart = (offset - URL_CONTEXT_WINDOW).coerceAtLeast(0)
            val windowEnd = (offset + URL_CONTEXT_WINDOW).coerceAtMost(editable.length)
            val matcher = Patterns.WEB_URL.matcher(editable)
            matcher.region(windowStart, windowEnd)
            while (matcher.find()) {
                if (offset in matcher.start() until matcher.end()) {
                    val url = matcher.group()
                    return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                }
                if (matcher.start() > offset) break
            }
            return null
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
            ) {
                if (!ready || applyingInternally || s !is Spanned) return
                removedCount += count
                checklistSnapshot =
                    s.getSpans(0, s.length, ChecklistItemSpan::class.java).map { span ->
                        val spanStart = s.getSpanStart(span)
                        ChecklistItemSnapshot(
                            paragraphStart = paragraphRange(spanStart, spanStart).first,
                            checked = span.checked,
                            span = span,
                        )
                    }
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                if (!ready || applyingInternally) return
                changedStart = if (changedStart < 0) start else min(changedStart, start)
                changedEnd = max(changedEnd, start + count)
                insertedCount += count
            }

            override fun afterTextChanged(s: Editable?) {
                if (!ready || applyingInternally || s == null) return
                val start = changedStart
                val end = changedEnd.coerceAtMost(s.length)
                val inserted = insertedCount
                val removed = removedCount
                val snapshot = checklistSnapshot
                changedStart = -1
                changedEnd = -1
                insertedCount = 0
                removedCount = 0
                checklistSnapshot = emptyList()
                if (start < 0) return
                applyingInternally = true
                try {
                    processTextChange(s, start, end, inserted, removed, snapshot)
                } finally {
                    applyingInternally = false
                }
                emitState()
            }
        }

        /**
         * Reconciles spans with a just-committed text change: styles newly typed text with the
         * pending inline styles, restores checklist items the change knocked loose, continues or
         * ends a checklist for every newline in the inserted region (soft keyboards commit Enter
         * inside a batched replace, so the region is scanned rather than expecting a lone '\n'),
         * and re-snaps every checklist span to its paragraph.
         */
        private fun processTextChange(
            s: Editable,
            start: Int,
            end: Int,
            inserted: Int,
            removed: Int,
            snapshot: List<ChecklistItemSnapshot>,
        ) {
            if (inserted > 0 && end > start) {
                for (style in pendingInline) applyInlineStyle(s, style, start, end)
            }
            reanchorChecklistItems(s, snapshot, start, inserted - removed)
            if (inserted > 0) {
                for (i in start until end) {
                    if (s[i] == '\n') handleChecklistNewline(s, i)
                }
            }
            normalizeChecklistSpans(s)
            if (inserted > 0 && end > start) applyAutoDetectedLinks(s, start, end)
        }

        /**
         * Handles a just-typed newline at [newlineIndex] for checklist continuation. If the line that
         * ended was a checklist item with content, the new line immediately becomes a checklist item —
         * even while still empty — so its checkbox shows at once. Enter on an empty item ends the list.
         */
        private fun handleChecklistNewline(
            editable: Editable,
            newlineIndex: Int,
        ) {
            val prevLineStart =
                TextUtils
                    .lastIndexOf(editable, '\n', (newlineIndex - 1).coerceAtLeast(0))
                    .let { if (it < 0) 0 else it + 1 }
            // Only a span that starts on the previous line counts; the line before it can bleed a
            // SPAN_PARAGRAPH across the boundary, and removing that would wrongly clear another item.
            val prevChecklist = checklistSpanStartingIn(editable, prevLineStart, newlineIndex) ?: return
            if (newlineIndex == prevLineStart) {
                // Enter on an empty item ends the list.
                editable.removeSpan(prevChecklist)
                return
            }
            applyChecklistToParagraphAt(editable, newlineIndex + 1)
        }

        /**
         * Restores checklist items that a text change detached. When a change covers a span's whole
         * range the span either collapses to a point that rides to the end of the inserted text
         * (a zero-length item's position, or an item fully rewritten by the IME) or is dropped from
         * the editable outright. Each such item from [snapshot] is re-applied to its paragraph
         * (whose start is remapped across the change by [delta]) with its checked state preserved.
         */
        private fun reanchorChecklistItems(
            editable: Editable,
            snapshot: List<ChecklistItemSnapshot>,
            changeStart: Int,
            delta: Int,
        ) {
            for (item in snapshot) {
                val mapped =
                    if (item.paragraphStart > changeStart) item.paragraphStart + delta else item.paragraphStart
                if (isParagraphStart(editable, mapped)) reanchorItem(editable, item, mapped)
            }
        }

        private fun isParagraphStart(
            editable: Editable,
            offset: Int,
        ): Boolean =
            offset in 0..editable.length &&
                (offset == 0 || editable[offset - 1] == '\n')

        /** Re-applies [item] at [mapped] if its span was dropped or collapsed away by the change. */
        private fun reanchorItem(
            editable: Editable,
            item: ChecklistItemSnapshot,
            mapped: Int,
        ) {
            val current = editable.getSpanStart(item.span)
            val collapsedAway = current >= 0 && current == editable.getSpanEnd(item.span) && current != mapped
            if (current >= 0 && !collapsedAway) return
            if (collapsedAway) editable.removeSpan(item.span)
            applyChecklistToParagraphAt(editable, mapped, item.checked)
        }

        /** Adds a checklist span to the paragraph containing [at] unless it already starts one. */
        private fun applyChecklistToParagraphAt(
            editable: Editable,
            at: Int,
            checked: Boolean = false,
        ) {
            val (pStart, pEnd) = paragraphRange(at, at)
            if (checklistSpanStartingIn(editable, pStart, pEnd) == null) {
                setChecklistSpan(editable, pStart, paragraphSpanEnd(editable, pEnd), checked)
            }
        }

        /**
         * Marks [start]..[end] as a checklist item, picking the class by emptiness: a zero-length
         * item (empty trailing paragraph) gets the non-rendering [ChecklistItemSpan] base — see its
         * docs for why it must not carry a paragraph style — and any other item the rendering
         * [ChecklistSpan].
         */
        private fun setChecklistSpan(
            editable: Editable,
            start: Int,
            end: Int,
            checked: Boolean,
        ) {
            val span = if (end > start) ChecklistSpan(checked) else ChecklistItemSpan(checked)
            editable.setSpan(span, start, end, Spannable.SPAN_PARAGRAPH)
        }

        /**
         * The checklist span that starts in the paragraph [pStart]..[pEnd], or null. Filtering by start
         * offset ignores a previous paragraph's [ChecklistItemSpan] that a boundary query can return.
         */
        private fun checklistSpanStartingIn(
            editable: Editable,
            pStart: Int,
            pEnd: Int,
        ): ChecklistItemSpan? =
            editable
                .getSpans(
                    pStart,
                    maxOf((pStart + 1).coerceAtMost(editable.length), pEnd),
                    ChecklistItemSpan::class.java,
                ).firstOrNull { editable.getSpanStart(it) >= pStart }

        /**
         * Snaps every checklist span to exactly one paragraph and drops duplicates. A zero-length
         * span on an empty trailing paragraph is the canonical representation of an empty checklist
         * item (serialized as a bare `- [ ] ` by [MarkdownFormatter]) and is painted by [onDraw];
         * it must be the non-rendering [ChecklistItemSpan] base, so the classes are swapped here
         * whenever a paragraph's emptiness changed.
         */
        private fun normalizeChecklistSpans(editable: Editable) {
            val seenParagraphStarts = HashSet<Int>()
            editable.getSpans(0, editable.length, ChecklistItemSpan::class.java).forEach { span ->
                val spanStart = editable.getSpanStart(span)
                if (spanStart < 0) return@forEach
                val (pStart, pEnd) = paragraphRange(spanStart, spanStart)
                val paraEnd = paragraphSpanEnd(editable, pEnd)
                if (!seenParagraphStarts.add(pStart)) {
                    editable.removeSpan(span)
                    return@forEach
                }
                if ((span is ChecklistSpan) != (paraEnd > pStart)) {
                    editable.removeSpan(span)
                    setChecklistSpan(editable, pStart, paraEnd, span.checked)
                } else if (spanStart != pStart || editable.getSpanEnd(span) != paraEnd) {
                    editable.setSpan(span, pStart, paraEnd, Spannable.SPAN_PARAGRAPH)
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

            /** Characters scanned around the change/cursor when (re-)detecting raw URLs. */
            private const val URL_CONTEXT_WINDOW = 2048
        }
    }
