package org.skynetsoftware.skeletonnotes.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.Settings
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncResult
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for the Settings screen, managing Nextcloud connection
 * state and sync operations.
 */
class SettingsViewModel(
    private val getSettings: GetSettingsUseCase,
    private val setPeriodicSyncEnabled: SetPeriodicSyncEnabledUseCase,
    private val initiateNextcloudLogin: InitiateNextcloudLoginUseCase,
    private val pollNextcloudLogin: PollNextcloudLoginUseCase,
    private val syncNotesWithNextcloud: SyncNotesWithNextcloudUseCase,
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
                    initiateNextcloudLogin = AppDi.initiateNextcloudLoginUseCase,
                    pollNextcloudLogin = AppDi.pollNextcloudLoginUseCase,
                    syncNotesWithNextcloud = AppDi.syncNotesWithNextcloudUseCase,
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

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    private val _authEvents = MutableSharedFlow<NextcloudAuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<NextcloudAuthEvent> = _authEvents.asSharedFlow()

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

    fun setPeriodicSyncEnabled(checked: Boolean) {
        setPeriodicSyncEnabled.invoke(checked)
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

    /**
     * Triggers a full bidirectional sync with Nextcloud.
     */
    fun syncNow() {
        viewModelScope.launch {
            when (val result = syncNotesWithNextcloud()) {
                is Result.Success -> {
                    _syncResult.value = result.data
                }
                is Result.Failure -> {
                    _syncResult.value = SyncResult.Error(result.throwable)
                }
            }
        }
    }
}
