package org.skynetsoftware.skeletonnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skynetsoftware.skeletonnotes.domain.model.Note

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        view: android.view.View
    ) : RecyclerView.ViewHolder(view) {
        private val titleView = itemView.findViewById<android.widget.TextView>(R.id.note_title)
        private val previewView = itemView.findViewById<android.widget.TextView>(R.id.note_preview)

        fun bind(note: Note) {
            titleView.text = note.title
            previewView.text = stripMarkdown(note.content)
            itemView.setOnClickListener { onNoteClick(note) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}

private fun stripMarkdown(content: String): String {
    val sb = StringBuilder()
    for (line in content.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("# ")) continue
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            sb.append(trimmed.removePrefix("- ").removePrefix("* ")).append(' ')
        } else if (trimmed.startsWith("> ")) {
            sb.append(trimmed.removePrefix("> ")).append(' ')
        } else if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
            sb.append(trimmed.replaceFirst(Regex("^\\d+\\.\\s"), "")).append(' ')
        } else {
            sb.append(line.trim()).append(' ')
        }
    }
    return sb.toString().trim()
}
