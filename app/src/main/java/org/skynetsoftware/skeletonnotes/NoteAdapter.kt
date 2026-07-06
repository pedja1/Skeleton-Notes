package org.skynetsoftware.skeletonnotes

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.skynetsoftware.skeletonnotes.domain.model.Note

/**
 * Adapter for displaying notes in a GridView using the ViewHolder pattern
 * for view recycling.
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

    override fun getCount(): Int = notes.size

    override fun getItem(position: Int): Note = notes[position]

    override fun getItemId(position: Int): Long = notes[position].id.hashCode().toLong()

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
        private val previewView: TextView = item.findViewById(R.id.note_preview)

        /**
         * Binds note data to the view and sets click listener.
         */
        fun bind(note: Note, onNoteClick: (Note) -> Unit) {
            previewView.text = note.content
            item.setOnClickListener { onNoteClick(note) }
        }
    }
}
