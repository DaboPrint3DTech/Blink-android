package com.daboprint3dtech.blink.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.SnapshotResult
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.BlinkNetwork
import com.daboprint3dtech.blink.data.api.Clip
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

data class CameraUiState(
    val camera: BlinkCamera? = null,
    val network: BlinkNetwork? = null,
    val snapshotting: Boolean = false,
    val thumbnailVersion: Long? = null,
    val settings: AutoRefreshSettings = AutoRefreshSettings(),
    val attempts: Map<String, Instant> = emptyMap(),
    val clips: List<Clip> = emptyList(),
    val clipsLoading: Boolean = false,
    val clipsError: String? = null,
    val busy: Boolean = false,
)

private data class Local(
    val clips: List<Clip> = emptyList(),
    val clipsLoading: Boolean = false,
    val clipsError: String? = null,
    val busy: Boolean = false,
)

class CameraViewModel(private val container: AppContainer, private val cameraKey: String) : ViewModel() {
    private val repo = container.repository
    private val local = MutableStateFlow(Local())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val thumbnailFile get() = repo.thumbnails.file(cameraKey)

    val state: StateFlow<CameraUiState> = combine(
        combine(repo.homescreen, repo.snapshotsInProgress, repo.thumbnails.versions) { home, snaps, versions ->
            val camera = home?.camera(cameraKey)
            CameraUiState(
                camera = camera,
                network = camera?.let { home.network(it.networkId) },
                snapshotting = cameraKey in snaps,
                thumbnailVersion = versions[cameraKey],
            )
        },
        container.autoRefreshStore.settings,
        container.autoRefreshStore.lastAttempts,
        local,
    ) { base, settings, attempts, l ->
        base.copy(
            settings = settings,
            attempts = attempts,
            clips = l.clips,
            clipsLoading = l.clipsLoading,
            clipsError = l.clipsError,
            busy = l.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUiState())

    init {
        loadClips()
    }

    fun loadClips() {
        local.update { it.copy(clipsLoading = true, clipsError = null) }
        viewModelScope.launch {
            try {
                val camera = repo.homescreen.value?.camera(cameraKey)
                val clips = repo.loadClips(days = 14, maxPages = 4).filter { clip ->
                    camera != null && (clip.deviceId == camera.id || clip.deviceName == camera.name)
                }
                local.update { it.copy(clips = clips.take(20), clipsLoading = false) }
            } catch (e: Exception) {
                local.update { it.copy(clipsLoading = false, clipsError = e.userMessage()) }
            }
        }
    }

    private fun camera() = repo.homescreen.value?.camera(cameraKey)

    fun snapshot() {
        val camera = camera() ?: return
        viewModelScope.launch {
            when (val r = repo.snapshot(camera)) {
                SnapshotResult.Updated -> _messages.send("Miniatura aggiornata")
                SnapshotResult.NotChanged -> _messages.send("Comando inviato, la miniatura arriverà a breve")
                is SnapshotResult.Failed -> _messages.send(r.error.userMessage())
            }
        }
    }

    fun recordClip() = action("Registrazione clip avviata: sarà disponibile tra le clip fra qualche istante") {
        repo.recordClip(it)
    }

    fun setMotion(enabled: Boolean) = action(null) { repo.setMotionDetection(it, enabled) }

    private fun action(success: String?, block: suspend (BlinkCamera) -> Unit) {
        val camera = camera() ?: return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block(camera)
                success?.let { _messages.send(it) }
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    fun setAutoIncluded(included: Boolean) = updateSettings { s -> s.withCamera(cameraKey) { it.copy(included = included) } }

    fun setAutoInterval(minutes: Int?) = updateSettings { s -> s.withCamera(cameraKey) { it.copy(intervalMinutes = minutes) } }

    fun enableAutoRefresh() = updateSettings { it.copy(enabled = true) }

    private fun updateSettings(transform: (AutoRefreshSettings) -> AutoRefreshSettings) {
        viewModelScope.launch { container.updateAutoRefresh(transform) }
    }
}
