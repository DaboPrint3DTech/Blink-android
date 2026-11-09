package com.daboprint3dtech.blink.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.SnapshotResult
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.BlinkNetwork
import com.daboprint3dtech.blink.data.api.Homescreen
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.ui.components.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class HomeUiState(
    val home: Homescreen? = null,
    val refreshing: Boolean = false,
    val loadError: String? = null,
    val snapshots: Set<String> = emptySet(),
    val busyNetworks: Set<Long> = emptySet(),
    val busyCameras: Set<String> = emptySet(),
    val thumbnailVersions: Map<String, Long> = emptyMap(),
    val settings: AutoRefreshSettings = AutoRefreshSettings(),
    val attempts: Map<String, Instant> = emptyMap(),
    val lastUpdated: Instant? = null,
    val cameraPrefs: CameraPreferences = CameraPreferences(),
)

private data class LocalState(
    val refreshing: Boolean = false,
    val loadError: String? = null,
    val busyNetworks: Set<Long> = emptySet(),
    val busyCameras: Set<String> = emptySet(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val local = MutableStateFlow(LocalState())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val thumbnails get() = repo.thumbnails

    private val dataFlow = combine(repo.homescreen, repo.snapshotsInProgress, repo.thumbnails.versions, repo.lastUpdated) { home, snaps, versions, updated ->
        HomeUiState(home = home, snapshots = snaps, thumbnailVersions = versions, lastUpdated = updated)
    }

    val state: StateFlow<HomeUiState> = combine(
        dataFlow,
        local,
        container.autoRefreshStore.settings,
        container.autoRefreshStore.lastAttempts,
        container.cameraPreferencesStore.preferences,
    ) { data, l, settings, attempts, prefs ->
        data.copy(
            refreshing = l.refreshing,
            loadError = l.loadError,
            busyNetworks = l.busyNetworks,
            busyCameras = l.busyCameras,
            settings = settings,
            attempts = attempts,
            cameraPrefs = prefs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                repo.refresh()
                local.update { it.copy(loadError = null) }
            } catch (e: Exception) {
                local.update { it.copy(loadError = e.userMessage()) }
                if (repo.homescreen.value != null) _messages.send(e.userMessage())
            } finally {
                local.update { it.copy(refreshing = false) }
            }
        }
    }

    fun snapshot(camera: BlinkCamera) {
        viewModelScope.launch {
            when (val r = container.launchDetached { repo.snapshot(camera) }.await()) {
                SnapshotResult.Updated -> _messages.send("${camera.name}: miniatura aggiornata")
                SnapshotResult.NotChanged -> _messages.send("${camera.name}: comando inviato, la miniatura arriverà a breve")
                is SnapshotResult.Failed -> _messages.send("${camera.name}: ${r.error.userMessage()}")
            }
        }
    }

    fun snapshotAll() {
        val cameras = repo.homescreen.value?.cameras?.filter { it.isOnline }.orEmpty()
        if (cameras.isEmpty()) return
        viewModelScope.launch {
            _messages.send("Aggiornamento di ${cameras.size} miniature in corso…")
            val results = container.launchDetached { repo.snapshot(cameras) }.await()
            val failed = results.count { it.value is SnapshotResult.Failed }
            _messages.send(
                if (failed == 0) "Miniature aggiornate" else "Miniature aggiornate, $failed non riuscite",
            )
        }
    }

    fun setArmed(network: BlinkNetwork, armed: Boolean) {
        local.update { it.copy(busyNetworks = it.busyNetworks + network.id) }
        viewModelScope.launch {
            try {
                repo.setSystemArmed(network.id, armed)
                _messages.send(if (armed) "${network.name}: sistema armato" else "${network.name}: sistema disarmato")
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                local.update { it.copy(busyNetworks = it.busyNetworks - network.id) }
            }
        }
    }

    fun toggleFavorite(camera: BlinkCamera) {
        viewModelScope.launch { container.updateCameraPreferences { it.toggleFavorite(camera.key) } }
    }

    fun setMotion(camera: BlinkCamera, enabled: Boolean) {
        local.update { it.copy(busyCameras = it.busyCameras + camera.key) }
        viewModelScope.launch {
            try {
                repo.setMotionDetection(camera, enabled)
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                local.update { it.copy(busyCameras = it.busyCameras - camera.key) }
            }
        }
    }
}
