package org.skynetsoftware.skeletonnotes

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbarTitle = findViewById<android.widget.TextView>(R.id.toolbar_title)
        toolbarTitle.text = getString(R.string.settings_title)

        findViewById<ImageView>(R.id.toolbar_settings).visibility = View.GONE
        findViewById<ImageView>(R.id.toolbar_add_note).visibility = View.GONE

        findViewById<ImageView>(R.id.toolbar_back).apply {
            visibility = View.VISIBLE
            setOnClickListener { finish() }
        }
    }
}
