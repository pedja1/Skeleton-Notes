package org.skynetsoftware.skeletonnotes.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.Settings
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.usecase.ExportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ImportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncScheduler
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for the Settings screen, managing Nextcloud connection
 * state and sync operations.
 */
class SettingsViewModel(
    private val getSettings: GetSettingsUseCase,
    private val setPeriodicSyncEnabled: SetPeriodicSyncEnabledUseCase,
    private val setSyncInterval: SetSyncIntervalUseCase,
    private val setSyncOnlyOnUnmetered: SetSyncOnlyOnUnmeteredUseCase,
    private val initiateNextcloudLogin: InitiateNextcloudLoginUseCase,
    private val pollNextcloudLogin: PollNextcloudLoginUseCase,
    private val scheduler: NextcloudSyncScheduler,
    private val exportNotes: ExportNotesUseCase,
    private val importNotes: ImportNotesUseCase,
) : ViewModel() {

    companion object {
        /** Interval between login poll requests. */
        private const val POLL_INTERVAL_MS = 2_000L

        /** Overall polling budget; the poll token is valid for 20 minutes server-side. */
        private const val POLL_TIMEOUT_MS = 10 * 60 * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    getSettings = AppDi.getSettingsUseCase,
                    setPeriodicSyncEnabled = AppDi.setPeriodicSyncEnabledUseCase,
                    setSyncInterval = AppDi.setSyncIntervalUseCase,
                    setSyncOnlyOnUnmetered = AppDi.setSyncOnlyOnUnmeteredUseCase,
                    initiateNextcloudLogin = AppDi.initiateNextcloudLoginUseCase,
                    pollNextcloudLogin = AppDi.pollNextcloudLoginUseCase,
                    scheduler = AppDi.nextcloudSyncScheduler,
                    exportNotes = AppDi.exportNotesUseCase,
                    importNotes = AppDi.importNotesUseCase,
                )
            }
        }
    }

    val settings: Flow<Settings> = getSettings()

    private val _nextcloudServerUrl = MutableStateFlow<String?>(null)
    val nextcloudServerUrl: StateFlow<String?> get() = _nextcloudServerUrl.asStateFlow()

    private val _nextcloudLoginState = MutableStateFlow<NextcloudLoginState>(NextcloudLoginState.NotConnected)
    val nextcloudLoginState: StateFlow<NextcloudLoginState> = _nextcloudLoginState.asStateFlow()

    private val nextcloudLoginError = MutableStateFlow<String?>(null)

    private val _authEvents = MutableSharedFlow<NextcloudAuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<NextcloudAuthEvent> = _authEvents.asSharedFlow()

    private val _dataOperation = MutableStateFlow(DataOperation.NONE)
    val dataOperation: StateFlow<DataOperation> = _dataOperation.asStateFlow()

    private val _dataTransferEvents = MutableSharedFlow<DataTransferEvent>(extraBufferCapacity = 4)
    val dataTransferEvents: SharedFlow<DataTransferEvent> = _dataTransferEvents.asSharedFlow()

    /** Current import conflict that needs a user choice, or `null` when no prompt is pending. */
    private val _pendingConflict = MutableStateFlow<ImportConflictPrompt?>(null)
    /** Lifecycle-resilient import conflict prompt state rendered by [SettingsActivity]. */
    val pendingConflict: StateFlow<ImportConflictPrompt?> = _pendingConflict.asStateFlow()

    /** Set while an import is running; awaits the user's choice for the current conflict. */
    private var conflictDeferred: CompletableDeferred<ConflictResolution>? = null

    /** A resolution the user chose to apply to every remaining conflict in this import. */
    private var applyToAllResolution: ConflictResolution? = null

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            val initialSettings = settings.first()
            val connectionInfo = initialSettings.nextcloudConnectionInfo
            if (connectionInfo != null) {
                _nextcloudLoginState.value = NextcloudLoginState.Connected(connectionInfo)
            }
        }
    }

    /** Persists the periodic-sync toggle and reschedules the job accordingly. */
    fun setPeriodicSyncEnabled(checked: Boolean) {
        setPeriodicSyncEnabled.invoke(checked)
        scheduler.reschedulePeriodicSync()
    }

    /** Persists the sync interval and reschedules the periodic job. */
    fun setSyncInterval(minutes: Long) {
        setSyncInterval.invoke(minutes)
        scheduler.reschedulePeriodicSync()
    }

    /** Persists the network-type preference and reschedules the periodic job. */
    fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {
        setSyncOnlyOnUnmetered.invoke(onlyOnUnmetered)
        scheduler.reschedulePeriodicSync()
    }

    /**
     * Initiates the Nextcloud Login Flow v2 for the given [nextcloudServerUrl].
     * On success, emits [NextcloudAuthEvent.LaunchAuthUrl] so the activity opens
     * the login page in a Custom Tab and starts polling for completion.
     */
    fun initiateNextcloudLogin(nextcloudServerUrl: String) {
        val normalizedUrl = normalizeServerUrl(nextcloudServerUrl)
        if (normalizedUrl == null) {
            nextcloudLoginError.value = AppDi.application.getString(R.string.nextcloud_server_url_invalid)
            _nextcloudLoginState.value = NextcloudLoginState.LoginError
            return
        }
        pollingJob?.cancel()
        _nextcloudServerUrl.value = normalizedUrl
        _nextcloudLoginState.value = NextcloudLoginState.InitiatingLogin
        viewModelScope.launch {
            when (val result = initiateNextcloudLogin.invoke(normalizedUrl)) {
                is Result.Success -> {
                    val data = result.data
                    _nextcloudLoginState.value = NextcloudLoginState.WaitingForLogin
                    _authEvents.tryEmit(NextcloudAuthEvent.LaunchAuthUrl(data.loginUrl))
                    startPolling(data.token, data.endpoint)
                }
                is Result.Failure -> {
                    nextcloudLoginError.value = result.throwable.message
                    _nextcloudLoginState.value = NextcloudLoginState.LoginError
                }
            }
        }
    }

    /**
     * Repeatedly polls the Nextcloud server until the user completes the
     * browser-based login or the poll token expires. On success, transitions to
     * [NextcloudLoginState.Connected] and emits [NextcloudAuthEvent.LoginSucceeded]
     * so the activity can dismiss the Custom Tab.
     */
    private fun startPolling(token: String, endpoint: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val authenticated = withTimeoutOrNull(POLL_TIMEOUT_MS.milliseconds) {
                while (isActive) {
                    val status = pollNextcloudLogin.invoke(token, endpoint)
                    if (status is NextcloudPollStatus.Authenticated) {
                        return@withTimeoutOrNull status.info
                    }
                    delay(POLL_INTERVAL_MS.milliseconds)
                }
                null
            }
            if (authenticated != null) {
                _nextcloudLoginState.value = NextcloudLoginState.Connected(authenticated)
                _authEvents.tryEmit(NextcloudAuthEvent.LoginSucceeded)
            } else {
                _nextcloudLoginState.value = NextcloudLoginState.LoginError
            }
        }
    }

    /**
     * Validates and normalizes a user-entered server URL. A missing scheme defaults to `https://`;
     * any non-HTTPS scheme (including cleartext `http://`) is rejected so credentials are never sent
     * over an insecure connection. Returns the normalized URL, or `null` if invalid.
     */
    private fun normalizeServerUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        if (!withScheme.startsWith("https://", ignoreCase = true)) return null
        val host = withScheme.removePrefix("https://").substringBefore('/')
        if (host.isBlank()) return null
        return withScheme.trimEnd('/')
    }

    /** Enqueues a one-off immediate sync job on any network. */
    fun syncNow() {
        scheduler.syncNow()
    }

    /**
     * Exports all notes to the document at [uri], writing a ZIP archive. No-op if another data
     * operation is already running. Emits [DataTransferEvent.ExportSuccess] or
     * [DataTransferEvent.Error] when finished.
     */
    fun export(uri: Uri) {
        if (_dataOperation.value != DataOperation.NONE) return
        _dataOperation.value = DataOperation.EXPORT
        viewModelScope.launch {
            val event = try {
                val outputStream = AppDi.application.contentResolver.openOutputStream(uri)
                    ?: error("Cannot open output stream")
                outputStream.use { stream ->
                    when (val result = exportNotes(stream)) {
                        is Result.Success -> DataTransferEvent.ExportSuccess(result.data)
                        is Result.Failure -> DataTransferEvent.Error
                    }
                }
            } catch (t: Throwable) {
                Log.w("export", null, t)
                DataTransferEvent.Error
            }
            _dataOperation.value = DataOperation.NONE
            _dataTransferEvents.emit(event)
        }
    }

    /**
     * Imports notes from the ZIP archive at [uri]. No-op if another data operation is already
     * running. For every id collision, emits [DataTransferEvent.ConflictPrompt] and suspends
     * until [onConflictResolved] is called. Emits [DataTransferEvent.ImportSuccess] or
     * [DataTransferEvent.Error] when finished.
     */
    fun import(uri: Uri) {
        if (_dataOperation.value != DataOperation.NONE) return
        _dataOperation.value = DataOperation.IMPORT
        applyToAllResolution = null
        viewModelScope.launch {
            val event = try {
                val inputStream = AppDi.application.contentResolver.openInputStream(uri)
                    ?: error("Cannot open input stream")
                inputStream.use { stream ->
                    when (val result = importNotes(stream) { existing, incoming -> resolveConflict(existing, incoming) }) {
                        is Result.Success -> DataTransferEvent.ImportSuccess(result.data)
                        is Result.Failure -> DataTransferEvent.Error
                    }
                }
            } catch (t: Throwable) {
                Log.w("export", null, t)
                DataTransferEvent.Error
            }
            conflictDeferred = null
            _pendingConflict.value = null
            _dataOperation.value = DataOperation.NONE
            _dataTransferEvents.emit(event)
        }
    }

    /** Records the active conflict, then suspends until [onConflictResolved] supplies a choice. */
    internal suspend fun resolveConflict(existing: Note, incoming: Note): ConflictResolution {
        applyToAllResolution?.let { return it }
        val deferred = CompletableDeferred<ConflictResolution>()
        conflictDeferred = deferred
        _pendingConflict.value = ImportConflictPrompt(existing, incoming)
        return deferred.await()
    }

    /**
     * Delivers the user's choice for the current import conflict. When [applyToAll] is true the
     * same [resolution] is applied to every subsequent conflict without prompting again.
     */
    fun onConflictResolved(resolution: ConflictResolution, applyToAll: Boolean) {
        if (applyToAll) applyToAllResolution = resolution
        conflictDeferred?.complete(resolution)
        conflictDeferred = null
        _pendingConflict.value = null
    }
}
