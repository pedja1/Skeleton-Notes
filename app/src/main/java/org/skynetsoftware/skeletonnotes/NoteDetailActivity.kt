package org.skynetsoftware.skeletonnotes

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
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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
import org.skynetsoftware.skeletonnotes.domain.model.Attachment

/**
 * Activity for viewing and editing a single note. Supports both creating new notes
 * and editing existing ones. Content is stored as HTML and rendered via
 * [Html.fromHtml]. The formatting toolbar provides bold, italic, font size,
 * file/image attachment, and tag insertion.
 */
@Suppress("TooManyFunctions")
class NoteDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        private const val NEW_NOTE_ID = NoteDetailViewModel.NEW_NOTE_ID
    }

    private val viewModel by viewModels<NoteDetailViewModel>(factoryProducer = { NoteDetailViewModel.Factory })

    private lateinit var editTitle: EditText
    private lateinit var editContent: EditText
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarDelete: ImageView
    private lateinit var toolbarBack: ImageView
    private lateinit var formattingToolbar: View

    private var noteId: Long = NEW_NOTE_ID
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
        setContentView(R.layout.activity_note_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
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

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NEW_NOTE_ID)

        setupViews()
        setupToolbar()
        setupFocusListeners()
        setupFormattingToolbar()
        observeViewModel()

        if (noteId != NEW_NOTE_ID) {
            viewModel.loadNote(noteId)
        } else {
            toolbarTitle.setText(R.string.note_detail_new_note_title)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })
    }

    private fun setupViews() {
        editTitle = findViewById(R.id.edit_note_title)
        editContent = findViewById(R.id.edit_note_content)
        toolbarTitle = findViewById(R.id.toolbar_title)
        toolbarDelete = findViewById(R.id.toolbar_delete)
        toolbarBack = findViewById(R.id.toolbar_back)
        formattingToolbar = findViewById(R.id.formatting_toolbar)

        findViewById<ImageView>(R.id.toolbar_settings).visibility = View.GONE
        findViewById<ImageView>(R.id.toolbar_add_note).visibility = View.GONE
    }

    private fun setupFocusListeners() {
        editTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) formattingToolbar.visibility = View.GONE
        }
        editContent.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) formattingToolbar.visibility = View.VISIBLE
        }
    }

    private fun setupToolbar() {
        toolbarBack.visibility = View.VISIBLE
        toolbarBack.setOnClickListener { saveAndFinish() }
        toolbarTitle.setText(R.string.note_detail_title)
        toolbarDelete.visibility = View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is NoteDetailViewModel.UiState.NewNote -> {
                            toolbarTitle.setText(R.string.note_detail_new_note_title)
                            toolbarDelete.visibility = View.GONE
                        }
                        is NoteDetailViewModel.UiState.NoteLoaded -> {
                            toolbarTitle.setText(R.string.note_detail_title)
                            toolbarDelete.visibility = View.VISIBLE
                            toolbarDelete.setOnClickListener { showDeleteConfirmation() }
                            editTitle.setText(state.note.title ?: "")
                            editContent.text = SpannableStringBuilder(
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
        val title = editTitle.text?.toString()?.trim()?.ifEmpty { null }
        val content = editContent.text?.let {
            Html.toHtml(it)
        } ?: ""
        if (viewModel.isNewNote() && title == null && content.isBlank()) {
            finish()
            return
        }
        viewModel.saveNote(title, content, attachments)
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

    private fun setupFormattingToolbar() {
        findViewById<ImageView>(R.id.format_bold).setOnClickListener { toggleBold() }
        findViewById<ImageView>(R.id.format_italic).setOnClickListener { toggleItalic() }
        findViewById<ImageView>(R.id.format_font_size).setOnClickListener { showFontSizeDialog() }
        findViewById<ImageView>(R.id.format_attach_file).setOnClickListener { pickFile() }
        findViewById<ImageView>(R.id.format_attach_image).setOnClickListener { pickImage() }
        findViewById<ImageView>(R.id.format_add_tag).setOnClickListener { insertTag() }
    }

    private fun toggleBold() {
        toggleSpan(StyleSpan(Typeface.BOLD))
    }

    private fun toggleItalic() {
        toggleSpan(StyleSpan(Typeface.ITALIC))
    }

    private fun toggleSpan(span: StyleSpan) {
        val editable = editContent.text
        val selectionStart = editContent.selectionStart
        val selectionEnd = editContent.selectionEnd
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
        val editable = editContent.text
        val selectionStart = editContent.selectionStart
        val selectionEnd = editContent.selectionEnd
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
        val editable = editContent.text
        val cursorPos = editContent.selectionStart
        editable.insert(cursorPos, "#")
    }

    private fun onAttachmentPicked(uri: Uri?, isImage: Boolean) {
        if (uri == null) return

        val attachment = Attachment(id = 0, noteId = viewModel.getNoteId(), uri = uri.toString())
        attachments.add(attachment)

        if (isImage) {
            val editable = editContent.text
            val cursorPos = editContent.selectionStart
            val imgTag = "<img src=\"$uri\">"
            editable.insert(cursorPos, imgTag)
        } else {
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
                val uri = Uri.parse(source)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val maxWidth = resources.displayMetrics.widthPixels - (2 * 16 * resources.displayMetrics.density).toInt()
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
