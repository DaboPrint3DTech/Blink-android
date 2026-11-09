package com.daboprint3dtech.blink.ui.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.api.Clip
import com.daboprint3dtech.blink.ui.components.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClipsUiState(
    val clips: List<Clip> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val cameraFilter: String? = null,
    val days: Long = 7,
) {
    val cameraNames: List<String> get() = clips.map { it.deviceName }.filter { it.isNotBlank() }.distinct().sorted()
    val visible: List<Clip> get() = clips.filter { cameraFilter == null || it.deviceName == cameraFilter }
}

class ClipsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ClipsUiState())
    val state: StateFlow<ClipsUiState> = _state.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    fun load(days: Long = _state.value.days) {
        _state.update { it.copy(loading = true, error = null, days = days) }
        viewModelScope.launch {
            try {
                val clips = container.repository.loadClips(days = days, maxPages = 10)
                _state.update { it.copy(clips = clips, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun setFilter(camera: String?) = _state.update { it.copy(cameraFilter = camera) }

    fun delete(clip: Clip) {
        viewModelScope.launch {
            try {
                container.repository.deleteClip(clip)
                _state.update { s -> s.copy(clips = s.clips.filterNot { it.id == clip.id }) }
                _messages.send("Clip eliminata")
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            }
        }
    }
}
