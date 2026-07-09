package org.skynetsoftware.skeletonnotes.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
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
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.databinding.ActivityMainBinding
import org.skynetsoftware.skeletonnotes.databinding.DialogFilterBinding
import org.skynetsoftware.skeletonnotes.note.NoteDetailActivity
import org.skynetsoftware.skeletonnotes.settings.SettingsActivity

/**
 * Main activity that displays the grid of notes with search, and filter controls.
 */
class MainActivity : ComponentActivity() {

    private lateinit var adapter: NoteAdapter

    private val mainViewMode by viewModels<MainViewModel>(factoryProducer = { MainViewModel.Factory })
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val gridView = binding.gridNotes
        gridView.layoutManager =
            StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        adapter = NoteAdapter { note ->
            val intent = Intent(this, NoteDetailActivity::class.java)
            intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
            startActivity(intent)
        }
        gridView.adapter = adapter

        binding.toolbar.toolbarAddNote.setOnClickListener {
            startActivity(Intent(this, NoteDetailActivity::class.java))
        }

        binding.toolbar.toolbarSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupSearch()
        setupFilter()

        val textNoNotes = binding.textNoNotes

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewMode.uiState.collect { state ->
                    when (state) {
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
                            if (state.notes.isEmpty()) {
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
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            mainViewMode.setQuery(text.toString())
        }
    }

    private fun setupFilter() {
        val filterIcon = binding.iconFilter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewMode.filter.collect {
                    if (it.showTrashed || it.showArchived) {
                        filterIcon.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, R.color.filter_active),
                            PorterDuff.Mode.SRC_IN
                        )
                    } else {
                        filterIcon.clearColorFilter()
                    }
                }
            }
        }

        filterIcon.setOnClickListener {
            val dialogBinding = DialogFilterBinding.inflate(layoutInflater)
            val checkboxTrash = dialogBinding.checkboxShowTrash
            val checkboxArchived = dialogBinding.checkboxShowArchived

            checkboxTrash.isChecked = mainViewMode.filter.value.showTrashed
            checkboxArchived.isChecked = mainViewMode.filter.value.showArchived

            AlertDialog.Builder(this)
                .setTitle(R.string.filter_title)
                .setView(dialogBinding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewMode.setFilter(checkboxArchived.isChecked, checkboxTrash.isChecked)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
