package org.skynetsoftware.skeletonnotes

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Main activity that displays the grid of notes with search, and filter controls.
 */
class MainActivity : ComponentActivity() {

    private lateinit var adapter: NoteAdapter

    private val mainViewMode by viewModels<MainViewModel>(factoryProducer = { MainViewModel.Factory })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val gridView = findViewById<GridView>(R.id.grid_notes)
        gridView.numColumns = 2

        adapter = NoteAdapter(this) { note ->
            val intent = Intent(this, NoteDetailActivity::class.java)
            intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
            startActivity(intent)
        }
        gridView.adapter = adapter

        val addNoteIcon = findViewById<ImageView>(R.id.toolbar_add_note)
        addNoteIcon.setOnClickListener {
            startActivity(Intent(this, NoteDetailActivity::class.java))
        }

        val settingsIcon = findViewById<ImageView>(R.id.toolbar_settings)
        settingsIcon.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupSearch()
        setupFilter()

        val textNoNotes = findViewById<TextView>(R.id.text_no_notes)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewMode.uiState.collect { state ->
                    when(state) {
                        MainViewModel.UiState.Error -> {
                            textNoNotes.visibility = View.VISIBLE
                            textNoNotes.text = getString(R.string.notes_list_error)
                            gridView.visibility = View.GONE
                        }
                        MainViewModel.UiState.Loading -> {
                            textNoNotes.visibility = View.VISIBLE
                            textNoNotes.text = getString(R.string.notes_list_loading)
                            gridView.visibility = View.GONE
                        }
                        is MainViewModel.UiState.Notes -> {
                            if(state.notes.isEmpty()) {
                                textNoNotes.visibility = View.VISIBLE
                                gridView.visibility = View.GONE
                                textNoNotes.text = getString(R.string.notes_list_no_notes)
                            } else {
                                textNoNotes.visibility = View.GONE
                                gridView.visibility = View.VISIBLE
                            }
                            adapter.setNotes(state.notes)
                        }
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        searchInput.doOnTextChanged { text, _, _, _ ->
            mainViewMode.setQuery(text.toString())
        }
    }

    private fun setupFilter() {
        val filterIcon = findViewById<ImageView>(R.id.icon_filter)
        updateFilterIcon(filterIcon)

        filterIcon.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_filter, null)
            val checkboxTrash = dialogView.findViewById<CheckBox>(R.id.checkbox_show_trash)
            val checkboxArchived = dialogView.findViewById<CheckBox>(R.id.checkbox_show_archived)

            checkboxTrash.isChecked = mainViewMode.isShowTrash()
            checkboxArchived.isChecked = mainViewMode.isShowArchived()

            AlertDialog.Builder(this)
                .setTitle(R.string.filter_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewMode.setShowTrash(checkboxTrash.isChecked)
                    mainViewMode.setShowArchived(checkboxArchived.isChecked)
                    updateFilterIcon(filterIcon)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateFilterIcon(filterIcon: ImageView) {
        if (mainViewMode.isFilterActive()) {
            filterIcon.setColorFilter(
                ContextCompat.getColor(this, R.color.filter_active),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            filterIcon.clearColorFilter()
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewMode.refresh()
    }
}
