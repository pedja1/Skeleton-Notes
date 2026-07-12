package org.skynetsoftware.skeletonnotes.home

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            // No-op: notifications are only shown after sync failure
        }

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

        adapter =
            NoteAdapter(lifecycleScope, onNoteClick = { note ->
                val intent = Intent(this, NoteDetailActivity::class.java)
                intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            }, onTagClick = { tag ->
                binding.searchInput.setText("#$tag")
            })
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
        maybeRequestNotificationPermission()
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
                    filterIcon.isSelected = !it.showActive || it.showTrashed || it.showArchived
                }
            }
        }

        filterIcon.setOnClickListener {
            val dialogBinding = DialogFilterBinding.inflate(layoutInflater)
            val checkboxActive = dialogBinding.checkboxShowActive
            val checkboxTrash = dialogBinding.checkboxShowTrash
            val checkboxArchived = dialogBinding.checkboxShowArchived

            checkboxActive.isChecked = mainViewMode.filter.value.showActive
            checkboxTrash.isChecked = mainViewMode.filter.value.showTrashed
            checkboxArchived.isChecked = mainViewMode.filter.value.showArchived

            AlertDialog
                .Builder(this)
                .setTitle(R.string.filter_title)
                .setView(dialogBinding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mainViewMode.setFilter(
                        checkboxActive.isChecked,
                        checkboxArchived.isChecked,
                        checkboxTrash.isChecked,
                    )
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /**
     * Requests the runtime notification permission (Android 13+) so background sync-failure
     * notifications can be shown.
     */
    private fun maybeRequestNotificationPermission(skipRationaleCheck: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            mainViewMode.shouldStopRequestingNotificationPermissionRationale()
        ) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!skipRationaleCheck &&
            shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        ) {
            showNotificationPermissionRationale()
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Show dialog explaining why notification permission is requested
     */
    private fun showNotificationPermissionRationale() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.notification_permission_rationale_title)
            .setMessage(R.string.notification_permission_rationale_message)
            .setPositiveButton(R.string.notification_permission_rationale_ok) { _, _ ->
                maybeRequestNotificationPermission(true)
            }.setNegativeButton(R.string.notification_permission_rationale_cancel) { _, _ -> }
            .setCancelable(false)
            .show()
        mainViewMode.setStopRequestingNotificationPermission()
    }
}
