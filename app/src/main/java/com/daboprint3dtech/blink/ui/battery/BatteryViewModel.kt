package com.daboprint3dtech.blink.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daboprint3dtech.blink.AppContainer
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.prefs.BatteryStatus
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatteryUiState(
    val cameras: List<BlinkCamera> = emptyList(),
    val prefs: CameraPreferences = CameraPreferences(),
    val refreshing: Boolean = false,
) {
    /** Telecamere a batteria, dalla più scarica. */
    val batteryCameras: List<BlinkCamera>
        get() = cameras.filter { BatteryStatus.hasBattery(it) }
            .sortedWith(compareBy({ !BatteryStatus.isLow(it, prefs) }, { it.batteryLevel ?: 99 }, { it.name }))

    /** Telecamere alimentate (senza batteria). */
    val poweredCameras: List<BlinkCamera>
        get() = cameras.filter { !BatteryStatus.hasBattery(it) }.sortedBy { it.name }

    val lowCount: Int get() = batteryCameras.count { BatteryStatus.isLow(it, prefs) }
}

class BatteryViewModel(private val container: AppContainer) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<BatteryUiState> = combine(
        container.repository.homescreen,
        container.cameraPreferencesStore.preferences,
        refreshing,
    ) { home, prefs, busy ->
        BatteryUiState(cameras = home?.cameras.orEmpty(), prefs = prefs, refreshing = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            runCatching { container.repository.refresh() }
            refreshing.value = false
        }
    }

    fun setAlertEnabled(enabled: Boolean) =
        viewModelScope.launch { container.updateCameraPreferences { it.copy(batteryAlertEnabled = enabled) } }

    fun setAlertLevel(level: Int) =
        viewModelScope.launch {
            container.updateCameraPreferences {
                it.copy(batteryAlertLevel = level.coerceIn(CameraPreferences.MIN_LEVEL, CameraPreferences.MAX_LEVEL))
            }
        }
}
