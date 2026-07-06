package org.skynetsoftware.skeletonnotes.markdown

import android.content.Context
import android.text.Spannable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView

/**
 * A read-only [TextView] that renders markdown formatting.
 *
 * Automatically parses markdown, strips syntax delimiters,
 * and applies formatting spans on every [setText] call.
 * Uses [MarkdownParser], [MarkdownStripper] and [MarkdownSpanApplier].
 */
class MarkdownTextView : TextView {

    private var isInitialized = false
    private var isRendering = false
    private val parser = MarkdownParser()
    private val applier = MarkdownSpanApplier()

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        gravity = Gravity.TOP or Gravity.START
        isInitialized = true
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (isRendering || !isInitialized) {
            super.setText(text, type)
            return
        }

        val rawText = text?.toString() ?: ""
        val spans = parser.parse(rawText)
        val (cleanText, adjustedSpans) = MarkdownStripper.strip(rawText, spans)

        isRendering = true
        super.setText(cleanText, BufferType.SPANNABLE)
        isRendering = false

        val spannable = getText() as? Spannable ?: return
        applier.apply(spannable, adjustedSpans)
    }
}
