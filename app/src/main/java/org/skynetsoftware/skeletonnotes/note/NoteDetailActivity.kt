package org.skynetsoftware.skeletonnotes.note

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.databinding.ActivityNoteDetailBinding
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.util.bindAttachments
import org.skynetsoftware.skeletonnotes.util.bindImages
import java.io.File

/**
 * Activity for viewing and editing a single note. Supports both creating new notes
 * and editing existing ones. Content is stored as Markdown and rendered into the editor's
 * span model via [MarkdownFormatter]. The formatting toolbar provides bold, italic, paragraph
 * styles (H1/H2/Paragraph), and file/image attachment.
 */
class NoteDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    private val viewModel by viewModels<NoteDetailViewModel>(factoryProducer = {
        NoteDetailViewModel.Factory(
            intent.getStringExtra(
                EXTRA_NOTE_ID,
            ),
        )
    })

    private lateinit var binding: ActivityNoteDetailBinding

    private val pickAttachmentLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            viewModel.onAttachmentPicked(uri)
        }

    private var pendingSaveAttachment: Attachment? = null

    private val saveAttachmentLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("*/*"),
        ) { uri ->
            val attachment = pendingSaveAttachment
            pendingSaveAttachment = null
            if (uri != null && attachment != null) {
                viewModel.saveAttachmentToUri(attachment, uri.toString())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom),
            )
            insets
        }

        setupViews()
        setupToolbar()
        setupFocusListeners()
        setupFormattingToolbar()
        observeViewModel()
        observeAttachments()
        observeToasts()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveAndFinish()
                }
            },
        )
    }

    private fun setupViews() {
        binding.toolbar.toolbarSettings.visibility = View.GONE
        binding.toolbar.toolbarAddNote.visibility = View.GONE
        binding.toolbar.toolbarDelete.visibility = View.GONE
    }

    private fun setupFocusListeners() {
        binding.editNoteTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.formattingToolbar.root.visibility = View.GONE
        }
        binding.editNoteContent.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.formattingToolbar.root.visibility = View.VISIBLE
        }
        binding.editNoteContent.requestFocus()
    }

    private fun setupToolbar() {
        binding.toolbar.toolbarBack.visibility = View.VISIBLE
        binding.toolbar.toolbarBack.setOnClickListener { saveAndFinish() }
        binding.toolbar.toolbarTitle.setText(R.string.note_detail_title)
        binding.toolbar.toolbarOverflow.visibility = View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is NoteDetailViewModel.UiState.NewNote -> {
                            binding.toolbar.toolbarTitle.setText(R.string.note_detail_new_note_title)
                            binding.toolbar.toolbarOverflow.visibility = View.GONE
                        }

                        is NoteDetailViewModel.UiState.NoteLoaded -> {
                            binding.toolbar.toolbarTitle.setText(R.string.note_detail_title)
                            binding.toolbar.toolbarOverflow.visibility = View.VISIBLE
                            binding.toolbar.toolbarOverflow.setOnClickListener { showOverflowMenu() }
                            binding.editNoteTitle.setText(state.note.title ?: "")
                            binding.editNoteContent.text =
                                SpannableStringBuilder(
                                    MarkdownFormatter.fromMarkdown(
                                        state.note.content,
                                    ),
                                )
                        }

                        NoteDetailViewModel.UiState.Saved -> finish()
                        NoteDetailViewModel.UiState.Deleted -> finish()
                        NoteDetailViewModel.UiState.MovedToTrash -> finish()
                        NoteDetailViewModel.UiState.Archived -> finish()
                        NoteDetailViewModel.UiState.Restored -> finish()
                        is NoteDetailViewModel.UiState.Error -> {
                            Toast
                                .makeText(
                                    this@NoteDetailActivity,
                                    state.throwable.message ?: getString(R.string.notes_list_error),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }

                        NoteDetailViewModel.UiState.Saving -> {}
                    }
                }
            }
        }
    }

    private fun observeAttachments() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.attachments.collect { attachments ->
                    val imageAttachments = attachments.filter { it.mimeType?.startsWith("image/") == true }
                    val otherAttachments = attachments.filter { it.mimeType?.startsWith("image/") != true }
                    binding.noteImages.bindImages(
                        paths = imageAttachments.map { it.uri },
                        scope = lifecycleScope,
                        onClick = { index ->
                            openAttachment(imageAttachments[index])
                        },
                        onLongClick = { index ->
                            showRemoveAttachmentConfirmation(imageAttachments[index])
                        },
                    )
                    binding.noteAttachments.bindAttachments(
                        otherAttachments,
                        onClick = { openAttachment(it) },
                        onLongClick = { showRemoveAttachmentConfirmation(it) },
                    )
                }
            }
        }
    }

    /**
     * Opens [attachment] in an external app via [Intent.ACTION_VIEW], sharing the local file through
     * the app's [FileProvider]. If no app can handle the file's type, falls back to letting the user
     * save the attachment to a location of their choosing via the Storage Access Framework.
     */
    private fun openAttachment(attachment: Attachment) {
        try {
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    File(attachment.uri),
                )
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, attachment.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            pendingSaveAttachment = attachment
            saveAttachmentLauncher.launch(
                attachment.filename ?: attachment.uri.substringAfterLast('/'),
            )
        }
    }

    private fun showRemoveAttachmentConfirmation(attachment: Attachment) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.remove_attachment_confirm_title)
            .setPositiveButton(R.string.remove_attachment_confirm_positive) { _, _ ->
                viewModel.onRemoveAttachment(attachment)
            }.setNegativeButton(R.string.delete_note_confirm_negative, null)
            .show()
    }

    private fun observeToasts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showToast.collect {
                    Toast.makeText(this@NoteDetailActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveAndFinish() {
        val title =
            binding.editNoteTitle.text
                ?.toString()
                ?.trim()
                ?.ifEmpty { null }
        val content =
            binding.editNoteContent.text?.let {
                MarkdownFormatter.toMarkdown(it)
            } ?: ""
        val plainText = binding.editNoteContent.text?.toString() ?: ""
        if (viewModel.isNewNote() &&
            title == null &&
            content.isBlank() &&
            viewModel.attachments.value.isEmpty()
        ) {
            finish()
            return
        }
        viewModel.saveNote(title, content, plainText)
    }

    private fun showDeleteConfirmation() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.delete_note_confirm_title)
            .setMessage(R.string.delete_note_confirm_message)
            .setPositiveButton(R.string.delete_note_confirm_positive) { _, _ ->
                viewModel.deleteNote()
            }.setNegativeButton(R.string.delete_note_confirm_negative, null)
            .show()
    }

    private fun showOverflowMenu() {
        val popupMenu = PopupMenu(this, binding.toolbar.toolbarOverflow)
        popupMenu.menuInflater.inflate(R.menu.note_detail_overflow, popupMenu.menu)
        val status = viewModel.noteStatus()
        if (status != null) {
            val archiveItem = popupMenu.menu.findItem(R.id.action_archive)
            val trashItem = popupMenu.menu.findItem(R.id.action_move_to_trash)
            when (status) {
                NoteStatus.ARCHIVE -> {
                    archiveItem.setTitle(R.string.unarchive_note)
                    trashItem.isVisible = false
                }
                NoteStatus.TRASH -> {
                    trashItem.setTitle(R.string.restore_from_trash)
                    archiveItem.isVisible = false
                }
                else -> { /* default titles from XML */ }
            }
        }
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_to_trash -> {
                    if (status == NoteStatus.TRASH) {
                        viewModel.restoreNote()
                    } else {
                        viewModel.moveToTrash()
                    }
                    true
                }

                R.id.action_archive -> {
                    if (status == NoteStatus.ARCHIVE) {
                        viewModel.restoreNote()
                    } else {
                        viewModel.archiveNote()
                    }
                    true
                }

                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun setupFormattingToolbar() {
        binding.formattingToolbar.formatBold.setOnClickListener { toggleBold() }
        binding.formattingToolbar.formatItalic.setOnClickListener { toggleItalic() }
        binding.formattingToolbar.formatH1.setOnClickListener { applyHeading(1) }
        binding.formattingToolbar.formatH2.setOnClickListener { applyHeading(2) }
        binding.formattingToolbar.formatParagraph.setOnClickListener { applyHeading(null) }
        binding.formattingToolbar.formatAttachFile.setOnClickListener { pickAttachment() }
    }

    private fun toggleBold() {
        toggleSpan(StyleSpan(Typeface.BOLD))
    }

    private fun toggleItalic() {
        toggleSpan(StyleSpan(Typeface.ITALIC))
    }

    private fun toggleSpan(span: StyleSpan) {
        val editable = binding.editNoteContent.text
        val selectionStart = binding.editNoteContent.selectionStart
        val selectionEnd = binding.editNoteContent.selectionEnd
        val spannable = editable as Spannable
        val existingSpans = spannable.getSpans(selectionStart, selectionEnd, span.javaClass)
        if (existingSpans.isNotEmpty()) {
            existingSpans.forEach { spannable.removeSpan(it) }
        } else {
            spannable.setSpan(
                span,
                selectionStart,
                selectionEnd.coerceAtLeast(selectionStart),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /**
     * Applies a heading style ([level] 1 or 2) or reverts to a plain paragraph ([level] null)
     * to the paragraph(s) covered by the current selection. Headings are represented the same way
     * [Html.fromHtml] represents them: a [RelativeSizeSpan] plus a bold [StyleSpan].
     */
    private fun applyHeading(level: Int?) {
        val spannable = binding.editNoteContent.text as? Spannable ?: return
        val text = spannable.toString()

        val selectionStart = binding.editNoteContent.selectionStart.coerceAtLeast(0)
        val selectionEnd = binding.editNoteContent.selectionEnd.coerceAtLeast(0)

        val paragraphStart =
            text
                .lastIndexOf('\n', (selectionStart - 1).coerceAtLeast(0))
                .let { if (it < 0) 0 else it + 1 }
        val paragraphEnd = text.indexOf('\n', selectionEnd).let { if (it < 0) text.length else it }
        if (paragraphStart >= paragraphEnd) return

        spannable
            .getSpans(paragraphStart, paragraphEnd, RelativeSizeSpan::class.java)
            .forEach { spannable.removeSpan(it) }
        spannable
            .getSpans(paragraphStart, paragraphEnd, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
            .forEach { spannable.removeSpan(it) }

        if (level != null) {
            val scale = if (level == 1) MarkdownFormatter.H1_SCALE else MarkdownFormatter.H2_SCALE
            spannable.setSpan(
                RelativeSizeSpan(scale),
                paragraphStart,
                paragraphEnd,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE,
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                paragraphStart,
                paragraphEnd,
                Spannable.SPAN_EXCLUSIVE_INCLUSIVE,
            )
        }
    }

    private fun pickAttachment() {
        pickAttachmentLauncher.launch("*/*")
    }
}
