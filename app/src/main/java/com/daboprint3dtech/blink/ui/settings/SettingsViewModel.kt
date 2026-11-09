package com.daboprint3dtech.blink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.SnapshotResult
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.auth.AuthState
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.ui.components.userMessage
import com.daboprint3dtech.blink.work.AutoRefreshRun
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class SettingsUiState(
    val settings: AutoRefreshSettings = AutoRefreshSettings(),
    val cameras: List<BlinkCamera> = emptyList(),
    val lastRun: AutoRefreshRun? = null,
    val nextRunAt: Instant? = null,
    val email: String? = null,
    val runningNow: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val runningNow = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        container.autoRefreshStore.settings,
        container.repository.homescreen,
        combine(container.autoRefreshEngine.lastRun, container.autoRefreshEngine.nextRunAt) { a, b -> a to b },
        container.auth.state,
        runningNow,
    ) { settings, home, (lastRun, next), auth, running ->
        SettingsUiState(
            settings = settings,
            cameras = home?.cameras.orEmpty(),
            lastRun = lastRun,
            nextRunAt = next,
            email = (auth as? AuthState.LoggedIn)?.session?.email,
            runningNow = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        if (container.repository.homescreen.value == null) {
            viewModelScope.launch { runCatching { container.repository.refresh() } }
        }
    }

    fun update(transform: (AutoRefreshSettings) -> AutoRefreshSettings) {
        viewModelScope.launch { container.updateAutoRefresh(transform) }
    }

    fun setCameraIncluded(key: String, included: Boolean) = update { s -> s.withCamera(key) { it.copy(included = included) } }

    fun setCameraInterval(key: String, minutes: Int?) = update { s -> s.withCamera(key) { it.copy(intervalMinutes = minutes) } }

    /** Scatta subito una miniatura per tutte le telecamere incluse (online). */
    fun refreshIncludedNow() {
        if (runningNow.value) return
        runningNow.value = true
        viewModelScope.launch {
            try {
                val settings = container.autoRefreshStore.current()
                val cameras = container.repository.refresh().cameras.filter { settings.isIncluded(it.key) && it.isOnline }
                if (cameras.isEmpty()) {
                    _messages.send("Nessuna telecamera inclusa e online")
                    return@launch
                }
                container.autoRefreshStore.recordAttempts(cameras.map { it.key }, Instant.now())
                val results = container.repository.snapshot(cameras)
                val failed = results.values.count { it is SnapshotResult.Failed }
                _messages.send(if (failed == 0) "Miniature aggiornate" else "$failed miniature non aggiornate")
                if (settings.enabled) container.refreshScheduler.restart(settings.unmeteredOnly)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                runningNow.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch { container.logout() }
    }
}
