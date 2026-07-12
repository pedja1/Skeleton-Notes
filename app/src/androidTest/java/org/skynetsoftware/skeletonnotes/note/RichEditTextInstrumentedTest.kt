package org.skynetsoftware.skeletonnotes.note

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [RichEditText]'s WYSIWYG span model: the inline toggles (bold/italic/strikethrough/
 * underline) with and without a selection (compose mode), heading application and reversion,
 * checklist toggling and Enter-driven continuation, checkbox tap handling, and the
 * [RichEditText.FormattingState] reported back to the toolbar. The widget is built, measured and
 * laid out off-screen (like the other view tests) so it owns a real text [android.text.Layout] for
 * the touch and formatting-state paths.
 */
@RunWith(AndroidJUnit4::class)
class RichEditTextInstrumentedTest {
    private lateinit var context: Context
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        context = instrumentation.targetContext
    }

    /** Builds a laid-out [RichEditText], optionally pre-loaded with [content], ready to interact with. */
    private fun buildEditor(content: CharSequence? = null): RichEditText {
        lateinit var editor: RichEditText
        instrumentation.runOnMainSync {
            editor = RichEditText(context)
            // A standalone EditText needs LayoutParams: mutating text triggers checkForResize(),
            // which reads layoutParams.width and would otherwise NPE on an unattached view.
            editor.layoutParams = ViewGroup.LayoutParams(WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (content != null) editor.setContentSilently(content)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            editor.measure(widthSpec, heightSpec)
            editor.layout(0, 0, editor.measuredWidth, editor.measuredHeight)
        }
        return editor
    }

    private val RichEditText.editable: Editable get() = text as Editable

    private fun boldSpans(editor: RichEditText): List<StyleSpan> =
        editor.editable
            .getSpans(0, editor.editable.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }

    private fun checklistSpans(editor: RichEditText): List<ChecklistSpan> =
        editor.editable.getSpans(0, editor.editable.length, ChecklistSpan::class.java).toList()

    /** Sends a single [action] touch to the editor at ([x], [y]); both events share a down time. */
    private fun touch(
        editor: RichEditText,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        editor.onTouchEvent(event)
        event.recycle()
    }

    @Test
    fun toggleBoldOnSelectionAppliesBoldSpan() {
        val editor = buildEditor()
        var applied = false
        instrumentation.runOnMainSync {
            editor.setText("bold me")
            editor.setSelection(0, editor.length())
            editor.toggleBold()
            applied = boldSpans(editor).isNotEmpty()
        }
        assertTrue("bold span should cover the selection", applied)
    }

    @Test
    fun toggleItalicOnSelectionAppliesItalicSpan() {
        val editor = buildEditor()
        var applied = false
        instrumentation.runOnMainSync {
            editor.setText("italic me")
            editor.setSelection(0, editor.length())
            editor.toggleItalic()
            applied =
                editor.editable
                    .getSpans(0, editor.editable.length, StyleSpan::class.java)
                    .any { it.style == Typeface.ITALIC }
        }
        assertTrue("italic span should cover the selection", applied)
    }

    @Test
    fun toggleStrikethroughOnSelectionAppliesSpan() {
        val editor = buildEditor()
        var applied = false
        instrumentation.runOnMainSync {
            editor.setText("struck")
            editor.setSelection(0, editor.length())
            editor.toggleStrikethrough()
            applied = editor.editable.getSpans(0, editor.editable.length, StrikethroughSpan::class.java).isNotEmpty()
        }
        assertTrue("strikethrough span should cover the selection", applied)
    }

    @Test
    fun toggleUnderlineOnSelectionAppliesSpan() {
        val editor = buildEditor()
        var applied = false
        instrumentation.runOnMainSync {
            editor.setText("underlined")
            editor.setSelection(0, editor.length())
            editor.toggleUnderline()
            applied = editor.editable.getSpans(0, editor.editable.length, UnderlineSpan::class.java).isNotEmpty()
        }
        assertTrue("underline span should cover the selection", applied)
    }

    @Test
    fun toggleBoldTwiceRemovesBoldSpan() {
        val editor = buildEditor()
        var stillBold = true
        instrumentation.runOnMainSync {
            editor.setText("toggle")
            editor.setSelection(0, editor.length())
            editor.toggleBold()
            editor.setSelection(0, editor.length())
            editor.toggleBold()
            stillBold = boldSpans(editor).isNotEmpty()
        }
        assertFalse("second toggle should remove the bold span", stillBold)
    }

    @Test
    fun pendingBoldStylesNextTypedText() {
        val editor = buildEditor()
        var typedIsBold = false
        instrumentation.runOnMainSync {
            editor.setText("ab")
            editor.setSelection(2)
            editor.toggleBold()
            editor.editable.insert(2, "c")
            typedIsBold =
                editor.editable
                    .getSpans(2, 3, StyleSpan::class.java)
                    .any { it.style == Typeface.BOLD }
        }
        assertTrue("text typed after a no-selection toggle should be bold", typedIsBold)
    }

    @Test
    fun applyHeadingLevel1AppliesSizeAndBold() {
        val editor = buildEditor()
        var hasSize = false
        var hasBold = false
        instrumentation.runOnMainSync {
            editor.setText("Heading")
            editor.setSelection(0, editor.length())
            editor.applyHeading(1)
            hasSize = editor.editable.getSpans(0, editor.editable.length, RelativeSizeSpan::class.java).isNotEmpty()
            hasBold = boldSpans(editor).isNotEmpty()
        }
        assertTrue("H1 should apply a relative size span", hasSize)
        assertTrue("H1 should apply a bold span", hasBold)
    }

    @Test
    fun applyHeadingLevel2AppliesSizeSpan() {
        val editor = buildEditor()
        var scale = 0f
        instrumentation.runOnMainSync {
            editor.setText("Sub heading")
            editor.setSelection(0, editor.length())
            editor.applyHeading(2)
            scale =
                editor.editable
                    .getSpans(0, editor.editable.length, RelativeSizeSpan::class.java)
                    .first()
                    .sizeChange
        }
        assertEquals(MarkdownFormatter.H2_SCALE, scale, TOLERANCE)
    }

    @Test
    fun applyHeadingNullRevertsToPlainParagraph() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("# Heading"))
        var sizeSpanCount = -1
        instrumentation.runOnMainSync {
            editor.setSelection(0, editor.length())
            editor.applyHeading(null)
            sizeSpanCount = editor.editable.getSpans(0, editor.editable.length, RelativeSizeSpan::class.java).size
        }
        assertEquals("reverting a heading removes its size span", 0, sizeSpanCount)
    }

    @Test
    fun applyHeadingOnEmptyParagraphIsNoOp() {
        val editor = buildEditor()
        var sizeSpanCount = -1
        instrumentation.runOnMainSync {
            editor.setText("")
            editor.setSelection(0)
            editor.applyHeading(1)
            sizeSpanCount = editor.editable.getSpans(0, editor.editable.length, RelativeSizeSpan::class.java).size
        }
        assertEquals("an empty paragraph cannot become a heading", 0, sizeSpanCount)
    }

    @Test
    fun headingLevelReportedForEachParagraph() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("# Title\n## Sub\nplain"))
        var inTitle: Int? = MISSING
        var inSub: Int? = MISSING
        var inPlain: Int? = MISSING
        instrumentation.runOnMainSync {
            var current: RichEditText.FormattingState? = null
            editor.onFormattingStateChanged = { current = it }
            editor.setSelection(2)
            inTitle = current?.headingLevel
            editor.setSelection(7)
            inSub = current?.headingLevel
            editor.setSelection(12)
            inPlain = current?.headingLevel
        }
        assertEquals(1, inTitle)
        assertEquals(2, inSub)
        assertNull(inPlain)
    }

    @Test
    fun toggleChecklistAddsSpanAndReportsState() {
        val editor = buildEditor()
        var spanCount = 0
        var reportedChecklist = false
        instrumentation.runOnMainSync {
            var current: RichEditText.FormattingState? = null
            editor.onFormattingStateChanged = { current = it }
            editor.setText("task")
            editor.setSelection(0)
            editor.toggleChecklist()
            spanCount = checklistSpans(editor).size
            reportedChecklist = current?.checklist == true
        }
        assertEquals(1, spanCount)
        assertTrue("formatting state should report the checklist as active", reportedChecklist)
    }

    @Test
    fun toggleChecklistTwiceRemovesSpan() {
        val editor = buildEditor()
        var spanCount = -1
        instrumentation.runOnMainSync {
            editor.setText("task")
            editor.setSelection(0)
            editor.toggleChecklist()
            editor.setSelection(0)
            editor.toggleChecklist()
            spanCount = checklistSpans(editor).size
        }
        assertEquals("second toggle should remove the checklist span", 0, spanCount)
    }

    @Test
    fun inlineStylesReportedWhenCursorEntersStyledText() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("**bold** ~~strike~~"))
        var boldReported = false
        var strikeReported = false
        instrumentation.runOnMainSync {
            var current: RichEditText.FormattingState? = null
            editor.onFormattingStateChanged = { current = it }
            editor.setSelection(2)
            boldReported = current?.bold == true
            editor.setSelection(7)
            strikeReported = current?.strikethrough == true
        }
        assertTrue("cursor inside bold text should report bold active", boldReported)
        assertTrue("cursor inside struck text should report strikethrough active", strikeReported)
    }

    @Test
    fun applyingStyleAdjacentToSameStyleMergesSpans() {
        val editor = buildEditor()
        var count = -1
        var start = -1
        var end = -1
        instrumentation.runOnMainSync {
            editor.setText("abcdef")
            editor.setSelection(0, 3)
            editor.toggleBold()
            editor.setSelection(3, 6)
            editor.toggleBold()
            val spans = boldSpans(editor)
            count = spans.size
            start = editor.editable.getSpanStart(spans.first())
            end = editor.editable.getSpanEnd(spans.first())
        }
        assertEquals("adjacent bold runs should merge into one span", 1, count)
        assertEquals(0, start)
        assertEquals(6, end)
    }

    @Test
    fun removingStyleFromMiddleKeepsOuterPortions() {
        val editor = buildEditor()
        var count = -1
        var middleBold = true
        instrumentation.runOnMainSync {
            editor.setText("abcdef")
            editor.setSelection(0, 6)
            editor.toggleBold()
            editor.setSelection(2, 4)
            editor.toggleBold()
            count = boldSpans(editor).size
            middleBold = editor.editable.getSpans(3, 4, StyleSpan::class.java).any { it.style == Typeface.BOLD }
        }
        assertEquals("removing the middle splits bold into two spans", 2, count)
        assertFalse("the middle should no longer be bold", middleBold)
    }

    @Test
    fun formattingStateCallbackExposedViaGetter() {
        val editor = buildEditor()
        var exposed = false
        var reportedBold = false
        instrumentation.runOnMainSync {
            editor.onFormattingStateChanged = { reportedBold = it.bold }
            exposed = editor.onFormattingStateChanged != null
            editor.setText("hi")
            editor.setSelection(0, editor.length())
            editor.toggleBold()
        }
        assertTrue("the callback should be readable back", exposed)
        assertTrue("toggling bold on a selection reports bold active", reportedBold)
    }

    @Test
    fun defaultFormattingStateEmittedWhenNoSelection() {
        val editor = buildEditor()
        var state: RichEditText.FormattingState? = null
        instrumentation.runOnMainSync {
            editor.setText("abc")
            Selection.removeSelection(editor.text as Spannable)
            editor.onFormattingStateChanged = { state = it }
            editor.toggleBold()
        }
        assertTrue("a state should still be emitted", state != null)
        assertFalse(state!!.bold)
        assertNull(state.headingLevel)
    }

    @Test
    fun tappingCheckboxTogglesCheckedState() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("- [ ] task"))
        var before = true
        var after = false
        instrumentation.runOnMainSync {
            val span = checklistSpans(editor).first()
            before = span.checked
            val layout = editor.layout
            val midY = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f + editor.totalPaddingTop - editor.scrollY
            val x = editor.totalPaddingLeft + CHECKBOX_TAP_INSET
            touch(editor, MotionEvent.ACTION_DOWN, x, midY)
            touch(editor, MotionEvent.ACTION_UP, x, midY)
            after = span.checked
        }
        assertFalse("checkbox starts unchecked", before)
        assertTrue("tapping the checkbox marks it checked", after)
    }

    @Test
    fun tappingOutsideCheckboxDoesNotToggle() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("- [ ] task"))
        var after = true
        instrumentation.runOnMainSync {
            val span = checklistSpans(editor).first()
            val layout = editor.layout
            val midY = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f + editor.totalPaddingTop - editor.scrollY
            val x = (editor.totalPaddingLeft + editor.width).toFloat()
            touch(editor, MotionEvent.ACTION_DOWN, x, midY)
            touch(editor, MotionEvent.ACTION_UP, x, midY)
            after = span.checked
        }
        assertFalse("tapping the text, not the checkbox, must not toggle it", after)
    }

    @Test
    fun typingNewlineAfterChecklistContinuesList() {
        val editor = buildEditor()
        var spanCount = 0
        var starts = emptyList<Int>()
        instrumentation.runOnMainSync {
            editor.setText("milk")
            editor.setSelection(0)
            editor.toggleChecklist()
            editor.setSelection(editor.length())
            editor.editable.insert(4, "\n")
            editor.editable.insert(5, "eggs")
            val spans = checklistSpans(editor)
            spanCount = spans.size
            starts = spans.map { editor.editable.getSpanStart(it) }.sorted()
        }
        assertEquals("both list lines should be checklist items", 2, spanCount)
        assertEquals(listOf(0, 5), starts)
    }

    @Test
    fun enterOnEmptyChecklistItemStopsList() {
        val editor = buildEditor()
        var spanCount = -1
        instrumentation.runOnMainSync {
            editor.setText("milk")
            editor.setSelection(0)
            editor.toggleChecklist()
            editor.setSelection(editor.length())
            editor.editable.insert(4, "\n") // Enter: arms an empty continuation line
            editor.editable.insert(5, "\n") // Enter on the empty item: ends the list
            spanCount = checklistSpans(editor).size
        }
        assertEquals("pressing Enter on an empty item leaves only the original item", 1, spanCount)
    }

    @Test
    fun cursorOnMarkdownLinkFiresContextCallback() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("a [link](https://example.com) b"))
        var contextUrl: String? = null
        instrumentation.runOnMainSync {
            editor.onLinkContextChanged = { url, _, _ -> contextUrl = url }
        }
        instrumentation.runOnMainSync {
            val urlSpans = editor.editable.getSpans(0, editor.length(), URLSpan::class.java)
            val cursorPos = editor.editable.getSpanStart(urlSpans.first()) + 1
            editor.setSelection(cursorPos)
        }
        assertEquals("https://example.com", contextUrl)
    }

    @Test
    fun cursorOnRawUrlFiresContextCallback() {
        val editor = buildEditor()
        var contextUrl: String? = null
        instrumentation.runOnMainSync {
            editor.onLinkContextChanged = { url, _, _ -> contextUrl = url }
        }
        instrumentation.runOnMainSync {
            editor.setText("see https://example.com/page for details")
            editor.setSelection(8)
        }
        assertEquals("https://example.com/page", contextUrl)
    }

    @Test
    fun cursorOnPlainTextFiresNullContext() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("plain text"))
        var contextUrl: String? = "not null"
        instrumentation.runOnMainSync {
            editor.onLinkContextChanged = { url, _, _ -> contextUrl = url }
        }
        instrumentation.runOnMainSync {
            editor.setSelection(3)
        }
        assertEquals(null, contextUrl)
    }

    @Test
    fun selectionDoesNotFireContextUrl() {
        val editor = buildEditor(MarkdownFormatter.fromMarkdown("[link](https://example.com)"))
        var contextUrl: String? = "not null"
        instrumentation.runOnMainSync {
            editor.onLinkContextChanged = { url, _, _ -> contextUrl = url }
        }
        instrumentation.runOnMainSync {
            val urlSpans = editor.editable.getSpans(0, editor.length(), URLSpan::class.java)
            val start = editor.editable.getSpanStart(urlSpans.first())
            editor.setSelection(start, editor.editable.getSpanEnd(urlSpans.first()))
        }
        assertEquals("selection should not produce a URL context", null, contextUrl)
    }

    private companion object {
        private const val WIDTH = 600
        private const val TOLERANCE = 0.05f
        private const val CHECKBOX_TAP_INSET = 4f
        private const val MISSING: Int = Int.MIN_VALUE
    }
}
