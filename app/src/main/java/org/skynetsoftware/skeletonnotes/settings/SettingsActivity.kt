package org.skynetsoftware.skeletonnotes.settings

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.databinding.ActivitySettingsBinding
import org.skynetsoftware.skeletonnotes.databinding.DialogImportConflictBinding
import org.skynetsoftware.skeletonnotes.databinding.DialogNextcloudServerUrlBinding
import org.skynetsoftware.skeletonnotes.databinding.ItemSettingsClickableBinding
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Settings
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Activity for managing application settings using a ListView
 * with dynamically populated items of different types.
 */
class SettingsActivity : ComponentActivity() {
    companion object {
        private val LAST_SYNC_FORMAT = SimpleDateFormat.getDateTimeInstance()
        private val EXPORT_FILE_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
    }

    private val settingsViewModel by viewModels<SettingsViewModel>(factoryProducer = { SettingsViewModel.Factory })
    private lateinit var binding: ActivitySettingsBinding

    private var showNextcloudServerUrlDialog: AlertDialog? = null
    private var conflictDialog: AlertDialog? = null

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            uri?.let { settingsViewModel.export(it) }
        }

    private val importLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { settingsViewModel.import(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.toolbar.toolbarTitle.text = getString(R.string.settings_title)

        binding.toolbar.toolbarSettings.visibility = View.GONE
        binding.toolbar.toolbarAddNote.visibility = View.GONE

        binding.toolbar.toolbarBack.apply {
            visibility = View.VISIBLE
            setOnClickListener { finish() }
        }

        setupNextcloudSection()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.settings.collect { settings ->
                        updateNextcloudSync(settings)
                    }
                }
                launch {
                    settingsViewModel.nextcloudLoginState.collect { nextcloudLoginState ->
                        updateNextcloudConnectionState(nextcloudLoginState)
                    }
                }
                launch {
                    settingsViewModel.authEvents.collect { event ->
                        handleAuthEvent(event)
                    }
                }
                launch {
                    settingsViewModel.dataOperation.collect { operation ->
                        updateDataOperation(operation)
                    }
                }
                launch {
                    settingsViewModel.dataTransferEvents.collect { event ->
                        handleDataTransferEvent(event)
                    }
                }
                launch {
                    settingsViewModel.pendingConflict.collect { conflict ->
                        updateConflictPrompt(conflict)
                    }
                }
            }
        }
    }

    private fun updateDataOperation(operation: DataOperation) {
        setDataItemBusy(binding.itemExport, operation == DataOperation.EXPORT)
        setDataItemBusy(binding.itemImport, operation == DataOperation.IMPORT)
        val idle = operation == DataOperation.NONE
        binding.itemExport.root.isEnabled = idle
        binding.itemImport.root.isEnabled = idle
    }

    private fun setDataItemBusy(
        item: ItemSettingsClickableBinding,
        busy: Boolean,
    ) {
        item.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        item.itemArrow.visibility = if (busy) View.GONE else View.VISIBLE
    }

    private fun handleDataTransferEvent(event: DataTransferEvent) {
        when (event) {
            is DataTransferEvent.ExportSuccess ->
                Toast.makeText(this, getString(R.string.export_success, event.count), Toast.LENGTH_SHORT).show()

            is DataTransferEvent.ImportSuccess ->
                Toast
                    .makeText(
                        this,
                        getString(
                            R.string.import_success,
                            event.summary.imported,
                            event.summary.overwritten,
                            event.summary.skipped,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()

            DataTransferEvent.Error ->
                Toast.makeText(this, R.string.data_transfer_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateConflictPrompt(conflict: ImportConflictPrompt?) {
        if (conflict == null) {
            conflictDialog?.dismiss()
        } else {
            showConflictDialog(conflict.incoming)
        }
    }

    private fun showConflictDialog(incoming: Note) {
        val title = incoming.title?.takeIf { it.isNotBlank() } ?: getString(R.string.note_detail_new_note_title)
        conflictDialog?.dismiss()
        val binding = DialogImportConflictBinding.inflate(layoutInflater)
        val applyToAll = binding.applyToAll
        conflictDialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.import_conflict_title)
                .setMessage(getString(R.string.import_conflict_message, title))
                .setView(binding.root)
                .setCancelable(false)
                .setPositiveButton(R.string.import_conflict_overwrite) { _, _ ->
                    settingsViewModel.onConflictResolved(ConflictResolution.OVERWRITE, applyToAll.isChecked)
                }.setNeutralButton(R.string.import_conflict_keep_both) { _, _ ->
                    settingsViewModel.onConflictResolved(ConflictResolution.KEEP_BOTH, applyToAll.isChecked)
                }.setNegativeButton(R.string.import_conflict_keep_existing) { _, _ ->
                    settingsViewModel.onConflictResolved(ConflictResolution.KEEP_EXISTING, applyToAll.isChecked)
                }.setOnDismissListener { conflictDialog = null }
                .show()
    }

    private fun handleAuthEvent(event: NextcloudAuthEvent) {
        when (event) {
            is NextcloudAuthEvent.LaunchAuthUrl -> launchLogin(event.url.toUri())

            NextcloudAuthEvent.LoginSucceeded -> {
                // Bring this activity to the front, which dismisses the Custom Tab
                // that is stacked on top of it within the same task.
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    /**
     * Opens the login [url] in a Custom Tab when a Custom Tabs-capable browser is
     * available, so the tab can be auto-dismissed on success. Otherwise (or if the
     * detected browser cannot actually service the intent) falls back to the user's
     * default browser via [Intent.ACTION_VIEW].
     */
    private fun launchLogin(url: Uri) {
        val customTabsPackage = CustomTabsClient.getPackageName(this, null)
        if (customTabsPackage != null) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.intent.setPackage(customTabsPackage)
                customTabsIntent.launchUrl(this, url)
                return
            } catch (_: ActivityNotFoundException) {
                // A browser advertised the Custom Tabs service but cannot launch it;
                // fall through to the plain browser below.
            }
        }
        launchExternalBrowser(url)
    }

    private fun launchExternalBrowser(url: Uri) {
        val intent =
            Intent(Intent.ACTION_VIEW, url).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.nextcloud_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNextcloudSection() {
        // nextcloud sync section
        binding.sectionNextcloudSync.sectionTitle.text = getString(R.string.settings_section_nextcloud_sync)

        binding.itemNextcloudConnect.itemSubtitle.visibility = View.VISIBLE
        binding.itemNextcloudConnect.root.setOnClickListener {
            showNextcloudServerUrlDialog(settingsViewModel.nextcloudServerUrl.value)
        }

        binding.itemNextcloudPeriodicSync.itemTitle.text = getString(R.string.settings_item_periodic_sync_title)
        binding.itemNextcloudPeriodicSync.itemSubtitle.text = getString(R.string.settings_item_periodic_sync_subtitle)
        binding.itemNextcloudPeriodicSync.itemSubtitle.visibility = View.VISIBLE
        binding.itemNextcloudPeriodicSync.itemSwitch.setOnCheckedChangeListener { _, checked ->
            settingsViewModel.setPeriodicSyncEnabled(checked)
        }

        binding.itemNextcloudSyncInterval.itemTitle.text = getString(R.string.settings_item_sync_interval_title)
        binding.itemNextcloudSyncInterval.itemSubtitle.visibility = View.VISIBLE
        binding.itemNextcloudSyncInterval.root.setOnClickListener {
            showSyncIntervalDialog()
        }

        binding.itemNextcloudSyncOnlyOnUnmetered.itemTitle.text =
            getString(R.string.settings_item_sync_only_on_unmetered_title)
        binding.itemNextcloudSyncOnlyOnUnmetered.itemSubtitle.text =
            getString(R.string.settings_item_sync_only_on_unmetered_subtitle)
        binding.itemNextcloudSyncOnlyOnUnmetered.itemSubtitle.visibility = View.VISIBLE
        binding.itemNextcloudSyncOnlyOnUnmetered.itemSwitch.setOnCheckedChangeListener { _, checked ->
            settingsViewModel.setSyncOnlyOnUnmetered(checked)
        }

        binding.itemNextcloudSyncNow.itemTitle.text = getString(R.string.settings_item_sync_now_title)
        binding.itemNextcloudSyncNow.itemSubtitle.visibility = View.VISIBLE
        binding.itemNextcloudSyncNow.root.setOnClickListener {
            settingsViewModel.syncNow()
        }

        // data section
        binding.sectionData.sectionTitle.text = getString(R.string.settings_section_data)

        binding.itemImport.itemTitle.text = getString(R.string.settings_item_import_tile)
        binding.itemImport.itemSubtitle.text = getString(R.string.settings_item_import_subtile)
        binding.itemImport.itemSubtitle.visibility = View.VISIBLE
        binding.itemImport.root.setOnClickListener {
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }

        binding.itemExport.itemTitle.text = getString(R.string.settings_item_export_tile)
        binding.itemExport.itemSubtitle.text = getString(R.string.settings_item_export_subtile)
        binding.itemExport.itemSubtitle.visibility = View.VISIBLE
        binding.itemExport.root.setOnClickListener {
            val timestamp = EXPORT_FILE_TIMESTAMP_FORMAT.format(Date())
            exportLauncher.launch(getString(R.string.export_default_file_name, timestamp))
        }
    }

    private fun updateNextcloudSync(settings: Settings) {
        val nextcloudConnectionInfo = settings.nextcloudConnectionInfo

        if (nextcloudConnectionInfo == null) {
            binding.itemNextcloudConnect.itemTitle.text = getString(R.string.settings_item_connect_to_nextcloud_title)
            binding.itemNextcloudConnect.itemSubtitle.text =
                getString(R.string.settings_item_connect_to_nextcloud_subtitle)
        } else {
            binding.itemNextcloudConnect.itemTitle.text = getString(R.string.settings_item_nextcloud_connected_title)
            binding.itemNextcloudConnect.itemSubtitle.text =
                getString(
                    R.string.settings_item_nextcloud_connected_subtitle,
                    nextcloudConnectionInfo.username,
                    nextcloudConnectionInfo.serverUrl,
                )
        }
        if (settings.nextcloudLastSyncTimestamp <= 0L) {
            binding.itemNextcloudSyncNow.itemSubtitle.text =
                getString(
                    R.string.settings_item_nextcloud_last_sync,
                    getString(R.string.settings_item_nextcloud_last_sync_never),
                )
        } else {
            binding.itemNextcloudSyncNow.itemSubtitle.text =
                getString(
                    R.string.settings_item_nextcloud_last_sync,
                    LAST_SYNC_FORMAT.format(settings.nextcloudLastSyncTimestamp),
                )
        }
        binding.itemNextcloudPeriodicSync.itemSwitch.isChecked = settings.nextcloudPeriodicSyncEnabled
        val intervalLabel = syncIntervalLabel(settings.nextcloudSyncIntervalMinutes)
        binding.itemNextcloudSyncInterval.itemSubtitle.text =
            getString(R.string.settings_item_sync_interval_subtitle, intervalLabel)
        binding.itemNextcloudSyncOnlyOnUnmetered.itemSwitch.isChecked = settings.nextcloudSyncOnlyOnUnmetered
    }

    private fun updateNextcloudConnectionState(nextcloudLoginState: NextcloudLoginState) {
        when (nextcloudLoginState) {
            is NextcloudLoginState.Connected -> {
                binding.itemNextcloudConnect.itemTitle.text =
                    getString(R.string.settings_item_nextcloud_connected_title)
                binding.itemNextcloudConnect.itemSubtitle.text =
                    getString(
                        R.string.settings_item_nextcloud_connected_subtitle,
                        nextcloudLoginState.nextcloudConnectionInfo.username,
                        nextcloudLoginState.nextcloudConnectionInfo.serverUrl,
                    )
                binding.itemNextcloudConnect.progressBar.visibility = View.GONE
                binding.itemNextcloudConnect.itemArrow.visibility = View.VISIBLE
                binding.itemNextcloudConnect.root.isEnabled = true
                setNextcloudSyncOptionsVisible(true)
            }

            NextcloudLoginState.InitiatingLogin -> {
                binding.itemNextcloudConnect.itemTitle.text =
                    getString(R.string.settings_item_nextcloud_connecting_title)
                binding.itemNextcloudConnect.itemSubtitle.text =
                    getString(R.string.settings_item_nextcloud_connecting_subtitle)
                binding.itemNextcloudConnect.progressBar.visibility = View.VISIBLE
                binding.itemNextcloudConnect.itemArrow.visibility = View.GONE
                binding.itemNextcloudConnect.root.isEnabled = false
                setNextcloudSyncOptionsVisible(false)
            }

            NextcloudLoginState.NotConnected -> {
                binding.itemNextcloudConnect.itemTitle.text =
                    getString(R.string.settings_item_connect_to_nextcloud_title)
                binding.itemNextcloudConnect.itemSubtitle.text =
                    getString(R.string.settings_item_connect_to_nextcloud_subtitle)
                binding.itemNextcloudConnect.progressBar.visibility = View.GONE
                binding.itemNextcloudConnect.itemArrow.visibility = View.VISIBLE
                binding.itemNextcloudConnect.root.isEnabled = true
                setNextcloudSyncOptionsVisible(false)
            }

            NextcloudLoginState.WaitingForLogin -> {
                binding.itemNextcloudConnect.itemTitle.text =
                    getString(R.string.settings_item_nextcloud_connecting_title)
                binding.itemNextcloudConnect.itemSubtitle.text =
                    getString(R.string.settings_item_nextcloud_logging_in_subtitle)
                binding.itemNextcloudConnect.progressBar.visibility = View.VISIBLE
                binding.itemNextcloudConnect.itemArrow.visibility = View.GONE
                binding.itemNextcloudConnect.root.isEnabled = false
                setNextcloudSyncOptionsVisible(false)
            }

            NextcloudLoginState.LoginError -> {
                binding.itemNextcloudConnect.itemTitle.text =
                    getString(R.string.settings_item_connect_to_nextcloud_title)
                binding.itemNextcloudConnect.itemSubtitle.text =
                    getString(R.string.settings_item_nextcloud_connect_error_subtitle)
                binding.itemNextcloudConnect.progressBar.visibility = View.GONE
                binding.itemNextcloudConnect.itemArrow.visibility = View.VISIBLE
                binding.itemNextcloudConnect.root.isEnabled = true
                setNextcloudSyncOptionsVisible(false)
            }
        }
    }

    private fun setNextcloudSyncOptionsVisible(visible: Boolean) {
        if (visible) {
            binding.itemNextcloudSyncNow.root.visibility = View.VISIBLE
            binding.itemNextcloudPeriodicSync.root.visibility = View.VISIBLE
            binding.itemNextcloudSyncInterval.root.visibility = View.VISIBLE
            binding.itemNextcloudSyncOnlyOnUnmetered.root.visibility = View.VISIBLE
        } else {
            binding.itemNextcloudSyncNow.root.visibility = View.GONE
            binding.itemNextcloudPeriodicSync.root.visibility = View.GONE
            binding.itemNextcloudSyncInterval.root.visibility = View.GONE
            binding.itemNextcloudSyncOnlyOnUnmetered.root.visibility = View.GONE
        }
    }

    private fun showSyncIntervalDialog() {
        val options = syncIntervalOptions()
        val labels = options.map { (_, label) -> label }.toTypedArray()
        AlertDialog
            .Builder(this)
            .setTitle(R.string.settings_sync_interval_dialog_title)
            .setItems(labels) { _, which ->
                settingsViewModel.setSyncInterval(options[which].first)
            }.show()
    }

    private fun syncIntervalOptions(): List<Pair<Long, String>> =
        listOf(
            15L to getString(R.string.settings_sync_interval_15min),
            60L to getString(R.string.settings_sync_interval_1h),
            360L to getString(R.string.settings_sync_interval_6h),
            1440L to getString(R.string.settings_sync_interval_24h),
        )

    private fun syncIntervalLabel(minutes: Long): String =
        when (minutes) {
            15L -> getString(R.string.settings_sync_interval_15min)
            60L -> getString(R.string.settings_sync_interval_1h)
            1440L -> getString(R.string.settings_sync_interval_24h)
            else -> getString(R.string.settings_sync_interval_6h)
        }

    private fun showNextcloudServerUrlDialog(nextcloudServerUrl: String?) {
        if (showNextcloudServerUrlDialog?.isShowing == true) {
            return
        }
        val dialogBinding = DialogNextcloudServerUrlBinding.inflate(layoutInflater)
        val nextcloudServerUrlEditText = dialogBinding.nextcloudServerUrl

        nextcloudServerUrl?.let { nextcloudServerUrlEditText.setText(it) }

        showNextcloudServerUrlDialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.nextcloud_server_url_title)
                .setView(dialogBinding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    settingsViewModel.initiateNextcloudLogin(nextcloudServerUrlEditText.text.toString())
                }.setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener {
                    showNextcloudServerUrlDialog = null
                }.show()
    }
}
