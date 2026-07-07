package org.skynetsoftware.skeletonnotes

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

/**
 * Adapter for displaying notes in a GridView using the ViewHolder pattern
 * for view recycling. Applies different backgrounds based on note status.
 */
class NoteAdapter(
    private val context: Context,
    private val onNoteClick: (Note) -> Unit
) : BaseAdapter() {

    private var notes: List<Note> = emptyList()

    /**
     * Updates the notes list and triggers a refresh.
     */
    fun setNotes(notes: List<Note>) {
        this.notes = notes
        notifyDataSetChanged()
    }

    /**
     * Returns the total number of notes in the adapter.
     */
    override fun getCount(): Int = notes.size

    /**
     * Returns the [Note] at the given [position].
     */
    override fun getItem(position: Int): Note = notes[position]

    /**
     * Returns a stable ID for the item at [position], derived from the note's ID.
     */
    override fun getItemId(position: Int): Long = notes[position].id.hashCode().toLong()

    /**
     * Creates or reuses a view for the note at [position] using the ViewHolder pattern.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context)
                .inflate(R.layout.item_note_card, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        viewHolder.bind(getItem(position), onNoteClick)
        return view
    }

    /**
     * ViewHolder for recycling note card views.
     */
    class ViewHolder(private val item: View) {
        private val titleView: TextView = item.findViewById(R.id.note_tile)
        private val previewView: TextView = item.findViewById(R.id.note_preview)

        /**
         * Binds note data to the view and sets click listener.
         */
        fun bind(note: Note, onNoteClick: (Note) -> Unit) {
            titleView.text = note.title
            previewView.text = SpannableStringBuilder(
                Html.fromHtml(
                    note.content,
                    Html.FROM_HTML_MODE_LEGACY,
                    null,
                    null
                )
            )

            if(note.title.isNullOrBlank()) {
                titleView.visibility = View.GONE
            } else {
                titleView.visibility = View.VISIBLE
            }

            val backgroundRes = when (note.status) {
                NoteStatus.TRASH -> R.drawable.card_background_trash
                NoteStatus.ARCHIVE -> R.drawable.card_background_archive
                else -> R.drawable.card_background
            }
            item.setBackgroundResource(backgroundRes)

            item.setOnClickListener { onNoteClick(note) }
        }
    }
}
