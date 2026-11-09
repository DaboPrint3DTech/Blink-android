package com.daboprint3dtech.blink.ui.cameras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.prefs.CameraOrdering
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrganizeUiState(
    val cameras: List<BlinkCamera> = emptyList(),
    val prefs: CameraPreferences = CameraPreferences(),
) {
    /** Ordine mostrato: l'ordine personalizzato grezzo (senza portare le preferite in testa). */
    val ordered: List<BlinkCamera>
        get() = CameraOrdering.ordered(cameras, prefs.copy(favoritesFirst = false))
}

class OrganizeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<OrganizeUiState> = combine(
        container.repository.homescreen,
        container.cameraPreferencesStore.preferences,
    ) { home, prefs ->
        OrganizeUiState(cameras = home?.cameras.orEmpty(), prefs = prefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeUiState())

    init {
        if (container.repository.homescreen.value == null) {
            viewModelScope.launch { runCatching { container.repository.refresh() } }
        }
    }

    private fun update(transform: (CameraPreferences) -> CameraPreferences) {
        viewModelScope.launch { container.updateCameraPreferences(transform) }
    }

    /** Materializza l'ordine completo attuale (tutte le chiavi) prima di spostare. */
    private fun fullOrder(): List<String> {
        val s = state.value
        return CameraOrdering.orderedKeys(s.cameras, s.prefs.copy(favoritesFirst = false))
    }

    fun moveUp(camera: BlinkCamera) = update { it.copy(order = CameraOrdering.move(fullOrder(), camera.key, up = true)) }
    fun moveDown(camera: BlinkCamera) = update { it.copy(order = CameraOrdering.move(fullOrder(), camera.key, up = false)) }
    fun toggleFavorite(camera: BlinkCamera) = update { it.toggleFavorite(camera.key) }
    fun setFavoritesFirst(value: Boolean) = update { it.copy(favoritesFirst = value) }
}
