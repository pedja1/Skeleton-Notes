package org.skynetsoftware.skeletonnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        val recycler = findViewById<RecyclerView>(R.id.recycler_notes)
        recycler.layoutManager = GridLayoutManager(this, 2)

        adapter = NoteAdapter { note ->
            val intent = Intent(this, NoteDetailActivity::class.java)
            startActivity(intent)
        }
        recycler.adapter = adapter

        val settingsIcon = findViewById<ImageView>(R.id.toolbar_settings)
        settingsIcon.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val textNoNotes = findViewById<TextView>(R.id.text_no_notes)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewMode.uiState.collect { state ->
                    adapter.submitList(state.notes)

                    val isEmpty = !state.isLoading && state.notes.isEmpty()
                    textNoNotes.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
