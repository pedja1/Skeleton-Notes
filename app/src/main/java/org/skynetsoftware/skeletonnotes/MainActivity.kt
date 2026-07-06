package org.skynetsoftware.skeletonnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

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
            startActivity(intent)
        }
        gridView.adapter = adapter

        val settingsIcon = findViewById<ImageView>(R.id.toolbar_settings)
        settingsIcon.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

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
}
