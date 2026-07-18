package org.skynetsoftware.skeletonnotes.note

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
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
 * span model via [MarkdownFormatter]. A persistent bottom action toolbar provides formatting
 * toggle, attach, archive, trash, and delete actions.
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

    private var linkPopup: PopupWindow? = null
    private var pendingLinkUrl: String? = null

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
        setupBottomToolbar()
        setupFocusListeners()
        setupFormattingToolbar()
        observeViewModel()
        observeAttachments()
        observeToasts()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    dismissLinkPopup()
                    saveAndFinish()
                }
            },
        )
    }

    private fun setupViews() {
        binding.toolbar.toolbarSettings.visibility = View.GONE
        binding.toolbar.toolbarAddNote.visibility = View.GONE
        binding.editNoteContent.onLinkContextChanged = { url, x, y ->
            if (url != null) showLinkPopup(url, x, y) else dismissLinkPopup()
        }
    }

    /**
     * Enables the formatting toggle button only while the content editor has focus, and hides the
     * formatting toolbar when focus is lost so it cannot appear without the user toggling it.
     */
    private fun setupFocusListeners() {
        binding.editNoteContent.setOnFocusChangeListener { _, hasFocus ->
            binding.noteActionToolbar.actionFormat.isEnabled = hasFocus
            if (!hasFocus) {
                binding.noteActionToolbar.actionFormat.isSelected = false
                binding.formattingToolbar.root.visibility = View.GONE
            }
        }
        binding.editNoteContent.requestFocus()
    }

    private fun setupToolbar() {
        binding.toolbar.toolbarBack.visibility = View.VISIBLE
        binding.toolbar.toolbarBack.setOnClickListener { saveAndFinish() }
        binding.toolbar.toolbarTitle.setText(R.string.note_detail_title)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is NoteDetailViewModel.UiState.NewNote -> {
                            binding.toolbar.toolbarTitle.setText(R.string.note_detail_new_note_title)
                            updateActionButtons(null)
                        }

                        is NoteDetailViewModel.UiState.NoteLoaded -> {
                            binding.toolbar.toolbarTitle.setText(R.string.note_detail_title)
                            // The sticky state is re-delivered on every lifecycle restart;
                            // populating the editor again would wipe in-progress edits.
                            if (viewModel.shouldPopulateEditor()) {
                                binding.editNoteTitle.setText(state.note.title ?: "")
                                binding.editNoteContent.setContentSilently(
                                    MarkdownFormatter.fromMarkdown(state.note.content),
                                )
                            }
                            updateActionButtons(state.note.status)
                        }

                        NoteDetailViewModel.UiState.Saved -> finish()
                        NoteDetailViewModel.UiState.Deleted -> finish()
                        NoteDetailViewModel.UiState.MovedToTrash -> finish()
                        NoteDetailViewModel.UiState.Archived -> finish()
                        NoteDetailViewModel.UiState.Restored -> finish()
                        NoteDetailViewModel.UiState.CloseWithoutSaving -> finish()
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

    /** Opens [url] in an external browser via [Intent.ACTION_VIEW]. Shows a toast if no browser is available. */
    private fun openUrl(url: String) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast
                .makeText(
                    this,
                    R.string.cannot_open_link,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    /**
     * Shows a [PopupWindow] with an "Open link" action anchored at ([x], [y]) screen coordinates.
     * Dismisses and re-creates if the popup already exists so the position tracks the cursor.
     */
    private fun showLinkPopup(
        url: String,
        x: Float,
        y: Float,
    ) {
        dismissLinkPopup()
        pendingLinkUrl = url

        val popupView =
            LayoutInflater.from(this).inflate(R.layout.note_detail_popup_link, null)
        popupView.setOnClickListener {
            openUrl(url)
            dismissLinkPopup()
        }

        val popup =
            PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isFocusable = false
                isOutsideTouchable = true
                showAtLocation(binding.root, Gravity.START or Gravity.TOP, x.toInt(), y.toInt())
            }
        linkPopup = popup
    }

    /** Dismisses the currently visible link popup, if any. */
    private fun dismissLinkPopup() {
        linkPopup?.dismiss()
        linkPopup = null
        pendingLinkUrl = null
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

    /**
     * Wires the bottom action toolbar buttons and sets their tooltips.
     */
    private fun setupBottomToolbar() {
        val toolbar = binding.noteActionToolbar

        toolbar.actionFormat.setOnClickListener { toggleFormattingToolbar() }
        toolbar.actionAttach.setOnClickListener { pickAttachment() }
        toolbar.actionArchive.setOnClickListener {
            if (viewModel.noteStatus() == NoteStatus.ARCHIVE) {
                viewModel.restoreNote()
            } else {
                viewModel.archiveNote()
            }
        }
        toolbar.actionTrash.setOnClickListener {
            if (viewModel.noteStatus() == NoteStatus.TRASH) {
                viewModel.restoreNote()
            } else {
                viewModel.moveToTrash()
            }
        }
        toolbar.actionDelete.setOnClickListener { showDeleteConfirmation() }

        ViewCompat.setTooltipText(toolbar.actionFormat, getString(R.string.format_formatting))
        ViewCompat.setTooltipText(toolbar.actionAttach, getString(R.string.format_attach_file))
        ViewCompat.setTooltipText(toolbar.actionArchive, getString(R.string.archive_note))
        ViewCompat.setTooltipText(toolbar.actionTrash, getString(R.string.move_to_trash))
        ViewCompat.setTooltipText(toolbar.actionDelete, getString(R.string.delete_permanently))

        toolbar.actionFormat.isEnabled = false
        toolbar.actionArchive.isEnabled = false
        toolbar.actionTrash.isEnabled = false
        toolbar.actionDelete.isEnabled = false
    }

    /**
     * Updates the bottom action toolbar buttons based on the current [status] of the loaded note.
     * When [status] is null (new/unsaved note) all action buttons except attach are disabled.
     *
     * - [NoteStatus.ACTIVE]: archive and trash show their default icons with archive/trash labels.
     * - [NoteStatus.ARCHIVE]: archive switches to Unarchive label+icon, trash is disabled.
     * - [NoteStatus.TRASH]: trash switches to Restore label+icon, archive is disabled.
     */
    private fun updateActionButtons(status: NoteStatus?) {
        val toolbar = binding.noteActionToolbar
        if (status == null) {
            toolbar.actionArchive.isEnabled = false
            toolbar.actionTrash.isEnabled = false
            toolbar.actionDelete.isEnabled = false
            toolbar.actionArchive.setImageResource(R.drawable.ic_archive)
            toolbar.actionArchive.contentDescription = getString(R.string.archive_note)
            toolbar.actionTrash.setImageResource(R.drawable.ic_delete)
            toolbar.actionTrash.contentDescription = getString(R.string.move_to_trash)
            return
        }
        when (status) {
            NoteStatus.ACTIVE -> {
                toolbar.actionArchive.isEnabled = true
                toolbar.actionArchive.setImageResource(R.drawable.ic_archive)
                toolbar.actionArchive.contentDescription = getString(R.string.archive_note)
                ViewCompat.setTooltipText(toolbar.actionArchive, getString(R.string.archive_note))

                toolbar.actionTrash.isEnabled = true
                toolbar.actionTrash.setImageResource(R.drawable.ic_delete)
                toolbar.actionTrash.contentDescription = getString(R.string.move_to_trash)
                ViewCompat.setTooltipText(toolbar.actionTrash, getString(R.string.move_to_trash))

                toolbar.actionDelete.isEnabled = true
            }

            NoteStatus.ARCHIVE -> {
                toolbar.actionArchive.isEnabled = true
                toolbar.actionArchive.setImageResource(R.drawable.ic_unarchive)
                toolbar.actionArchive.contentDescription = getString(R.string.unarchive_note)
                ViewCompat.setTooltipText(toolbar.actionArchive, getString(R.string.unarchive_note))

                toolbar.actionTrash.isEnabled = false
                toolbar.actionTrash.setImageResource(R.drawable.ic_delete)
                toolbar.actionTrash.contentDescription = getString(R.string.move_to_trash)

                toolbar.actionDelete.isEnabled = true
            }

            NoteStatus.TRASH -> {
                toolbar.actionArchive.isEnabled = false
                toolbar.actionArchive.setImageResource(R.drawable.ic_archive)
                toolbar.actionArchive.contentDescription = getString(R.string.archive_note)

                toolbar.actionTrash.isEnabled = true
                toolbar.actionTrash.setImageResource(R.drawable.ic_restore_from_trash)
                toolbar.actionTrash.contentDescription = getString(R.string.restore_from_trash)
                ViewCompat.setTooltipText(toolbar.actionTrash, getString(R.string.restore_from_trash))

                toolbar.actionDelete.isEnabled = true
            }
        }
    }

    /**
     * Toggles the formatting toolbar visibility and reflects the active state on the formatting
     * toggle button.
     */
    private fun toggleFormattingToolbar() {
        val toolbar = binding.formattingToolbar
        val actionFormat = binding.noteActionToolbar.actionFormat
        if (toolbar.root.visibility == View.VISIBLE) {
            toolbar.root.visibility = View.GONE
            actionFormat.isSelected = false
        } else {
            toolbar.root.visibility = View.VISIBLE
            actionFormat.isSelected = true
        }
    }

    private fun setupFormattingToolbar() {
        val toolbar = binding.formattingToolbar
        val content = binding.editNoteContent

        toolbar.formatBold.setOnClickListener { content.toggleBold() }
        toolbar.formatItalic.setOnClickListener { content.toggleItalic() }
        toolbar.formatStrikethrough.setOnClickListener { content.toggleStrikethrough() }
        toolbar.formatUnderline.setOnClickListener { content.toggleUnderline() }
        toolbar.formatH1.setOnClickListener { content.applyHeading(1) }
        toolbar.formatH2.setOnClickListener { content.applyHeading(2) }
        toolbar.formatParagraph.setOnClickListener { content.applyHeading(null) }
        toolbar.formatChecklist.setOnClickListener { content.toggleChecklist() }

        ViewCompat.setTooltipText(toolbar.formatBold, getString(R.string.format_bold))
        ViewCompat.setTooltipText(toolbar.formatItalic, getString(R.string.format_italic))
        ViewCompat.setTooltipText(toolbar.formatStrikethrough, getString(R.string.format_strikethrough))
        ViewCompat.setTooltipText(toolbar.formatUnderline, getString(R.string.format_underline))
        ViewCompat.setTooltipText(toolbar.formatH1, getString(R.string.heading_h1))
        ViewCompat.setTooltipText(toolbar.formatH2, getString(R.string.heading_h2))
        ViewCompat.setTooltipText(toolbar.formatParagraph, getString(R.string.paragraph_normal))
        ViewCompat.setTooltipText(toolbar.formatChecklist, getString(R.string.format_checklist))

        content.onFormattingStateChanged = { state ->
            toolbar.formatBold.isSelected = state.bold
            toolbar.formatItalic.isSelected = state.italic
            toolbar.formatStrikethrough.isSelected = state.strikethrough
            toolbar.formatUnderline.isSelected = state.underline
            toolbar.formatH1.isSelected = state.headingLevel == 1
            toolbar.formatH2.isSelected = state.headingLevel == 2
            toolbar.formatChecklist.isSelected = state.checklist
        }
    }

    private fun pickAttachment() {
        pickAttachmentLauncher.launch("*/*")
    }
}
