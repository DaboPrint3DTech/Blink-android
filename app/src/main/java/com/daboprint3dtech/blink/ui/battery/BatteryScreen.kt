package com.daboprint3dtech.blink.ui.battery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.prefs.BatteryStatus
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import com.daboprint3dtech.blink.ui.components.SectionHeader
import com.daboprint3dtech.blink.ui.components.SwitchRow
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.theme.StatusColors

/** Azioni della schermata batterie. */
data class BatteryActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onAlertEnabled: (Boolean) -> Unit = {},
    val onAlertLevel: (Int) -> Unit = {},
)

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val vm = appViewModel { BatteryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setAlertEnabled(granted)
    }
    fun enableAlert(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setAlertEnabled(enabled)
        }
    }
    BatteryContent(
        state,
        BatteryActions(
            onBack = onBack,
            onRefresh = vm::refresh,
            onAlertEnabled = ::enableAlert,
            onAlertLevel = vm::setAlertLevel,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryContent(state: BatteryUiState, actions: BatteryActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stato batterie") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.cameras.isNotEmpty(),
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.cameras.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.refreshing) CircularProgressIndicator()
                    else Text("Nessuna telecamera", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@PullToRefreshBox
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    SwitchRow(
                        title = "Avvisami quando la batteria è scarica",
                        subtitle = if (state.lowCount > 0) "${state.lowCount} telecamere attualmente sotto soglia" else "Notifica quando una telecamera scende sotto la soglia",
                        checked = state.prefs.batteryAlertEnabled,
                        onCheckedChange = actions.onAlertEnabled,
                        icon = Icons.Outlined.NotificationsActive,
                    )
                    if (state.prefs.batteryAlertEnabled) {
                        ThresholdSelector(state.prefs.batteryAlertLevel, actions.onAlertLevel)
                    }
                }
                if (state.batteryCameras.isNotEmpty()) {
                    item { SectionHeader("Telecamere a batteria") }
                    items(state.batteryCameras, key = { it.key }) { camera ->
                        BatteryRow(camera, state.prefs)
                    }
                }
                if (state.poweredCameras.isNotEmpty()) {
                    item { SectionHeader("Alimentate") }
                    items(state.poweredCameras, key = { it.key }) { camera ->
                        PoweredRow(camera)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ThresholdSelector(level: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Soglia di avviso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val options = listOf(1 to "Scarica", 2 to "Media", 3 to "Anche piena")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = level == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }
        Text(
            "Avvisa quando il livello è ≤ $level/3, oppure Blink segnala \"scarica\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BatteryRow(camera: BlinkCamera, prefs: CameraPreferences) {
    val low = BatteryStatus.isLow(camera, prefs)
    val level = camera.batteryLevel
    val fraction = when {
        level != null -> (level.coerceIn(0, 3)) / 3f
        camera.isLowBattery -> 0.15f
        else -> 0.8f
    }
    val color = when {
        low -> StatusColors.Error
        fraction <= 0.4f -> StatusColors.Warning
        else -> StatusColors.Armed
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(camera.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (!camera.isOnline) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.CloudOff, contentDescription = "Offline", modifier = Modifier.size(16.dp), tint = StatusColors.Error)
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val stateLabel = when {
                    camera.isLowBattery -> "Batteria scarica"
                    level != null -> "Livello $level/3"
                    else -> "OK"
                }
                Text(stateLabel, style = MaterialTheme.typography.bodySmall, color = color)
                camera.wifiSignal?.let { MetaLabel(Icons.Outlined.Wifi, "$it/5") }
                camera.temperatureC?.let { MetaLabel(Icons.Outlined.DeviceThermostat, "%.0f°C".format(it)) }
            }
        }
    }
}

@Composable
private fun PoweredRow(camera: BlinkCamera) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Bolt, contentDescription = null, tint = StatusColors.Armed)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(camera.name, style = MaterialTheme.typography.bodyLarge)
            Text("Alimentata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        camera.wifiSignal?.let { MetaLabel(Icons.Outlined.Wifi, "$it/5") }
    }
}

@Composable
private fun MetaLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
