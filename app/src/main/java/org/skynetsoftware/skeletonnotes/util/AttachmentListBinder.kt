package org.skynetsoftware.skeletonnotes.util

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.domain.model.Attachment

/**
 * Binds a list of non-image [attachments] into a vertical [LinearLayout] as tappable rows.
 * Each row (see `item_attachment.xml`) shows a generic file icon and the attachment's display name,
 * falling back to the on-disk filename for legacy rows that have no stored [Attachment.filename].
 *
 * [onClick] is invoked when a row is tapped (e.g. to open the file); [onLongClick] when it is
 * long-pressed (e.g. to remove it). Passing an empty list hides the container. The rows are rebuilt
 * on each call — the list is small enough that view recycling is unnecessary.
 */
fun LinearLayout.bindAttachments(
    attachments: List<Attachment>,
    onClick: (Attachment) -> Unit = {},
    onLongClick: (Attachment) -> Unit = {},
) {
    removeAllViews()
    if (attachments.isEmpty()) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE

    val inflater = LayoutInflater.from(context)
    for (attachment in attachments) {
        val row = inflater.inflate(R.layout.item_attachment, this, false)
        row.findViewById<TextView>(R.id.attachment_name).text =
            attachment.filename ?: attachment.uri.substringAfterLast('/')
        row.setOnClickListener { onClick(attachment) }
        row.setOnLongClickListener {
            onLongClick(attachment)
            true
        }
        addView(row)
    }
}
