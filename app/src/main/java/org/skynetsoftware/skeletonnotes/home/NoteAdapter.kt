package org.skynetsoftware.skeletonnotes.home

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.databinding.ItemNoteCardBinding
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.note.MarkdownFormatter
import org.skynetsoftware.skeletonnotes.util.bindImages

/**
 * Adapter for displaying notes in a RecyclerView using a StaggeredGridLayoutManager,
 * which lets each card size itself to its content. Applies different backgrounds based
 * on note status.
 */
class NoteAdapter(
    private val scope: CoroutineScope,
    private val onNoteClick: (Note) -> Unit,
    private val onTagClick: (String) -> Unit
) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {

    private var notes: List<NoteWithAttachments> = emptyList()

    /**
     * Updates the notes list and dispatches minimal changes via [DiffUtil].
     */
    fun setNotes(notes: List<NoteWithAttachments>) {
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
        holder.bind(notes[position], scope, onNoteClick, onTagClick)
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
        fun bind(
            noteWithAttachments: NoteWithAttachments,
            scope: CoroutineScope,
            onNoteClick: (Note) -> Unit,
            onTagClick: (String) -> Unit
        ) {
            val note = noteWithAttachments.note

            binding.noteImages.bindImages(
                noteWithAttachments.attachments.filter { it.mimeType?.startsWith("image/") == true }.map { it.uri },
                scope
            )

            binding.noteTile.text = note.title
            binding.notePreview.text = MarkdownFormatter.fromMarkdown(
                note.content
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

            bindTags(note, onTagClick)

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

        /**
         * Renders the note's tags as chip pills, or hides the container when there are none.
         * Reuses the chips already present in the container (ViewHolders are recycled and the
         * chips are not recycled by RecyclerView), creating new ones only when the note has more
         * tags than there are existing chips, and hiding any surplus chips left over from a
         * previous, longer binding.
         */
        private fun bindTags(note: Note, onTagClick: (String) -> Unit) {
            val container = binding.noteTags
            if (note.tags.isEmpty()) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE
            var index = 0
            for (tag in note.tags) {
                val chip = container.getChildAt(index) as TextView?
                    ?: createTagChip(onTagClick).also { container.addView(it) }
                chip.visibility = View.VISIBLE
                chip.text = tag
                index++
            }
            for (i in index until container.childCount) {
                container.getChildAt(i).visibility = View.GONE
            }
        }

        /**
         * Creates a single tag chip [TextView] styled as a rounded pill. The chip text is set by
         * the caller so the chip can be reused across binds.
         */
        private fun createTagChip(onTagClick: (String) -> Unit): TextView {
            val context = binding.root.context
            val resources = context.resources
            val horizontalPadding =
                resources.getDimensionPixelSize(R.dimen.tag_chip_padding_horizontal)
            val verticalPadding =
                resources.getDimensionPixelSize(R.dimen.tag_chip_padding_vertical)
            val margin = resources.getDimensionPixelSize(R.dimen.tag_chip_margin)

            return TextView(context).apply {
                setBackgroundResource(R.drawable.tag_chip_background)
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                setTextColor(resources.getColor(R.color.tag_chip_text, context.theme))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.tag_chip_text_size)
                )
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                setOnClickListener { chip ->
                    val tag = (chip as? TextView)?.text?.toString() ?: return@setOnClickListener
                    onTagClick(tag)
                }
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, margin, margin) }
            }
        }
    }

    /**
     * Computes the difference between two note lists for efficient RecyclerView updates.
     */
    private class NoteDiffCallback(
        private val oldList: List<NoteWithAttachments>,
        private val newList: List<NoteWithAttachments>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].note.id == newList[newItemPosition].note.id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
