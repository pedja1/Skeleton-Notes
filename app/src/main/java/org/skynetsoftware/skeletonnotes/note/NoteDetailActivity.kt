package org.skynetsoftware.skeletonnotes.note

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.data.attachment.AttachmentStorageManager
import org.skynetsoftware.skeletonnotes.databinding.ActivityNoteDetailBinding
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import java.io.File
import java.util.UUID

/**
 * Activity for viewing and editing a single note. Supports both creating new notes
 * and editing existing ones. Content is stored as HTML and rendered via
 * [Html.fromHtml]. The formatting toolbar provides bold, italic, font size,
 * file/image attachment, and tag insertion.
 */
class NoteDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    private val viewModel by viewModels<NoteDetailViewModel>(factoryProducer = {
        NoteDetailViewModel.Factory(
            intent.getStringExtra(
                EXTRA_NOTE_ID
            )
        )
    })

    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var attachmentStorageManager: AttachmentStorageManager

    private val attachments = mutableListOf<Attachment>()

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        onAttachmentPicked(uri, isImage = false)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        onAttachmentPicked(uri, isImage = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        attachmentStorageManager = AttachmentStorageManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }

        setupViews()
        setupToolbar()
        setupFocusListeners()
        setupFormattingToolbar()
        observeViewModel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })
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
                            binding.editNoteContent.text = SpannableStringBuilder(
                                Html.fromHtml(
                                    state.note.content,
                                    Html.FROM_HTML_MODE_LEGACY,
                                    resolveImageGetter(),
                                    null
                                )
                            )
                            attachments.clear()
                            attachments.addAll(state.attachments)
                        }

                        NoteDetailViewModel.UiState.Saved -> finish()
                        NoteDetailViewModel.UiState.Deleted -> finish()
                        NoteDetailViewModel.UiState.MovedToTrash -> finish()
                        NoteDetailViewModel.UiState.Archived -> finish()
                        is NoteDetailViewModel.UiState.Error -> {
                            Toast.makeText(
                                this@NoteDetailActivity,
                                state.throwable.message ?: getString(R.string.notes_list_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        NoteDetailViewModel.UiState.Saving -> {}
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveAndFinish() {
        val title = binding.editNoteTitle.text?.toString()?.trim()?.ifEmpty { null }
        val content = binding.editNoteContent.text?.let {
            Html.toHtml(it)
        } ?: ""
        val plainText = binding.editNoteContent.text?.toString() ?: ""
        if (viewModel.isNewNote() && title == null && content.isBlank()) {
            finish()
            return
        }
        viewModel.saveNote(title, content, plainText, attachments)
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_note_confirm_title)
            .setMessage(R.string.delete_note_confirm_message)
            .setPositiveButton(R.string.delete_note_confirm_positive) { _, _ ->
                viewModel.deleteNote()
            }
            .setNegativeButton(R.string.delete_note_confirm_negative, null)
            .show()
    }

    private fun showOverflowMenu() {
        val popupMenu = PopupMenu(this, binding.toolbar.toolbarOverflow)
        popupMenu.menuInflater.inflate(R.menu.note_detail_overflow, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_move_to_trash -> {
                    viewModel.moveToTrash()
                    true
                }

                R.id.action_archive -> {
                    viewModel.archiveNote()
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
        binding.formattingToolbar.formatFontSize.setOnClickListener { showFontSizeDialog() }
        binding.formattingToolbar.formatAttachFile.setOnClickListener { pickFile() }
        binding.formattingToolbar.formatAttachImage.setOnClickListener { pickImage() }
        binding.formattingToolbar.formatAddTag.setOnClickListener { insertTag() }
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
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun showFontSizeDialog() {
        val sizes = arrayOf(
            getString(R.string.font_size_small),
            getString(R.string.font_size_normal),
            getString(R.string.font_size_large),
            getString(R.string.font_size_huge)
        )
        val sizeValues = intArrayOf(12, 16, 20, 24)
        AlertDialog.Builder(this)
            .setTitle(R.string.font_size_dialog_title)
            .setItems(sizes) { _, which ->
                applyFontSize(sizeValues[which])
            }
            .show()
    }

    private fun applyFontSize(sizeSp: Int) {
        val editable = binding.editNoteContent.text
        val selectionStart = binding.editNoteContent.selectionStart
        val selectionEnd = binding.editNoteContent.selectionEnd
        if (selectionStart == selectionEnd) return
        val spannable = editable as Spannable
        val existingSpans = spannable.getSpans(
            selectionStart, selectionEnd, AbsoluteSizeSpan::class.java
        )
        existingSpans.forEach { spannable.removeSpan(it) }
        val dip = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp.toFloat(),
            resources.displayMetrics
        ).toInt()
        spannable.setSpan(
            AbsoluteSizeSpan(dip),
            selectionStart,
            selectionEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun pickFile() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    private fun insertTag() {
        val editable = binding.editNoteContent.text
        val cursorPos = binding.editNoteContent.selectionStart
        editable.insert(cursorPos, "#")
    }

    private fun onAttachmentPicked(uri: Uri?, isImage: Boolean) {
        if (uri == null) return

        val attachmentId = UUID.randomUUID().toString()
        val filename = uri.lastPathSegment ?: "file"
        val localPath = attachmentStorageManager.copyToStorage(uri, attachmentId, filename)

        val attachment = Attachment(
            id = attachmentId,
            noteId = viewModel.noteId,
            uri = localPath
        )
        attachments.add(attachment)

        if (isImage) {
            val editable = binding.editNoteContent.text
            val cursorPos = binding.editNoteContent.selectionStart
            val imgTag = "<img src=\"$localPath\">"
            editable.insert(cursorPos, imgTag)
        } else {
            //TODO translate
            Toast.makeText(this, "File attached", Toast.LENGTH_SHORT).show()
        }

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    private fun resolveImageGetter(): Html.ImageGetter {
        return Html.ImageGetter { source ->
            try {
                val file = File(source)
                val bitmap = if (file.exists()) {
                    BitmapFactory.decodeFile(source)
                } else {
                    val uri = Uri.parse(source)
                    val inputStream = contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bmp
                }
                if (bitmap != null) {
                    val maxWidth =
                        resources.displayMetrics.widthPixels - (2 * 16 * resources.displayMetrics.density).toInt()
                    val scaledBitmap = if (bitmap.width > maxWidth) {
                        val scale = maxWidth.toFloat() / bitmap.width
                        bitmap.scale(maxWidth, (bitmap.height * scale).toInt())
                    } else {
                        bitmap
                    }
                    val drawable = scaledBitmap.toDrawable(resources)
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    drawable
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
