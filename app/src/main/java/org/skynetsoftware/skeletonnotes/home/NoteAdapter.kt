package org.skynetsoftware.skeletonnotes.home

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.databinding.ItemNoteCardBinding
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus

/**
 * Adapter for displaying notes in a RecyclerView using a StaggeredGridLayoutManager,
 * which lets each card size itself to its content. Applies different backgrounds based
 * on note status.
 */
class NoteAdapter(
    private val onNoteClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {

    private var notes: List<Note> = emptyList()

    /**
     * Updates the notes list and dispatches minimal changes via [DiffUtil].
     */
    fun setNotes(notes: List<Note>) {
        val diff = DiffUtil.calculateDiff(NoteDiffCallback(this.notes, notes))
        this.notes = notes
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Returns the total number of notes in the adapter.
     */
    override fun getItemCount(): Int = notes.size

    /**
     * Inflates a note card view and wraps it in a [ViewHolder].
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNoteCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    /**
     * Binds the note at [position] to the given [holder].
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(notes[position], onNoteClick)
    }

    /**
     * ViewHolder for a note card.
     */
    class ViewHolder(
        private val binding: ItemNoteCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds note data to the view and sets click listener.
         */
        fun bind(note: Note, onNoteClick: (Note) -> Unit) {
            binding.noteTile.text = note.title
            binding.notePreview.text = Html.fromHtml(
                note.content,
                Html.FROM_HTML_MODE_LEGACY,
                null,
                null
            ).trimEnd()

            if(note.title.isNullOrBlank()) {
                binding.noteTile.visibility = View.GONE
            } else {
                binding.noteTile.visibility = View.VISIBLE
            }

            if(note.content.isBlank()) {
                binding.notePreview.visibility = View.GONE
            } else {
                binding.notePreview.visibility = View.VISIBLE
            }

            binding.noteLastEdited.text = NoteTimeFormatter.format(
                note.modifiedAt,
                binding.root.context
            )

            val backgroundRes = when (note.status) {
                NoteStatus.TRASH -> R.drawable.card_background_trash
                NoteStatus.ARCHIVE -> R.drawable.card_background_archive
                else -> R.drawable.card_background
            }
            binding.root.setBackgroundResource(backgroundRes)

            binding.root.setOnClickListener { onNoteClick(note) }
        }
    }

    /**
     * Computes the difference between two note lists for efficient RecyclerView updates.
     */
    private class NoteDiffCallback(
        private val oldList: List<Note>,
        private val newList: List<Note>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].id == newList[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
