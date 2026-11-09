package com.daboprint3dtech.blink.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.BuildConfig
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.settings.ArmFilter
import com.daboprint3dtech.blink.data.settings.AutoRefreshPlanner
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.ui.components.ConfirmDialog
import com.daboprint3dtech.blink.ui.components.DABOPRINT3D_YOUTUBE_URL
import com.daboprint3dtech.blink.ui.components.openUrl
import com.daboprint3dtech.blink.ui.components.IntervalDialog
import com.daboprint3dtech.blink.ui.components.ListDivider
import com.daboprint3dtech.blink.ui.components.SectionHeader
import com.daboprint3dtech.blink.ui.components.SwitchRow
import com.daboprint3dtech.blink.ui.components.TimeOfDayDialog
import com.daboprint3dtech.blink.ui.components.ValueRow
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.components.relativeTime
import com.daboprint3dtech.blink.ui.components.upcomingTime
import com.daboprint3dtech.blink.ui.home.rememberNow

private sealed interface SettingsDialog {
    data object DefaultInterval : SettingsDialog
    data class CameraInterval(val camera: BlinkCamera) : SettingsDialog
    data object WindowStart : SettingsDialog
    data object WindowEnd : SettingsDialog
    data object Logout : SettingsDialog
}

/** Azioni della schermata impostazioni. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onUpdate: ((AutoRefreshSettings) -> AutoRefreshSettings) -> Unit = {},
    val onCameraIncluded: (String, Boolean) -> Unit = { _, _ -> },
    val onCameraInterval: (String, Int?) -> Unit = { _, _ -> },
    val onRunNow: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onRequestBattery: () -> Unit = {},
    val onOpenYoutube: () -> Unit = {},
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var ignoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        ignoringBattery = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    SettingsContent(
        state = state,
        snackbar = snackbar,
        ignoringBattery = ignoringBattery,
        actions = SettingsActions(
            onBack = onBack,
            onUpdate = vm::update,
            onCameraIncluded = vm::setCameraIncluded,
            onCameraInterval = vm::setCameraInterval,
            onRunNow = vm::refreshIncludedNow,
            onLogout = vm::logout,
            onRequestBattery = { requestIgnoreBatteryOptimizations(context) },
            onOpenYoutube = { openUrl(context, DABOPRINT3D_YOUTUBE_URL) },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    snackbar: SnackbarHostState,
    ignoringBattery: Boolean,
    actions: SettingsActions,
    now: java.time.Instant = rememberNow(),
) {
    val s = state.settings
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var armMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Aggiornamento automatico miniature")
            SwitchRow(
                title = "Aggiorna le miniature automaticamente",
                subtitle = "Le telecamere scattano una nuova immagine agli intervalli scelti, anche con l'app chiusa",
                checked = s.enabled,
                onCheckedChange = { v -> actions.onUpdate { it.copy(enabled = v) } },
                icon = Icons.Outlined.Autorenew,
            )
            ValueRow(
                title = "Intervallo predefinito",
                subtitle = "Ogni telecamera può avere un intervallo diverso (vedi sotto)",
                value = AutoRefreshSettings.formatInterval(s.defaultIntervalMinutes),
                onClick = { dialog = SettingsDialog.DefaultInterval },
                enabled = s.enabled,
                icon = Icons.Outlined.Schedule,
            )
            SwitchRow(
                title = "Solo in una fascia oraria",
                subtitle = if (s.windowEnabled) {
                    "Dalle ${AutoRefreshSettings.formatMinuteOfDay(s.windowStartMinute)} alle ${AutoRefreshSettings.formatMinuteOfDay(s.windowEndMinute)}"
                } else {
                    "Tutto il giorno"
                },
                checked = s.windowEnabled,
                onCheckedChange = { v -> actions.onUpdate { it.copy(windowEnabled = v) } },
                enabled = s.enabled,
                icon = Icons.Outlined.AccessTime,
            )
            if (s.windowEnabled) {
                ValueRow(
                    title = "Dalle",
                    value = AutoRefreshSettings.formatMinuteOfDay(s.windowStartMinute),
                    onClick = { dialog = SettingsDialog.WindowStart },
                    enabled = s.enabled,
                    modifier = Modifier.padding(start = 40.dp),
                )
                ValueRow(
                    title = "Alle",
                    value = AutoRefreshSettings.formatMinuteOfDay(s.windowEndMinute),
                    onClick = { dialog = SettingsDialog.WindowEnd },
                    enabled = s.enabled,
                    modifier = Modifier.padding(start = 40.dp),
                )
            }
            Box {
                ValueRow(
                    title = "Stato del sistema",
                    subtitle = "Quando eseguire gli aggiornamenti",
                    value = s.armFilter.label,
                    onClick = { armMenu = true },
                    enabled = s.enabled,
                    icon = Icons.Outlined.Shield,
                )
                DropdownMenu(expanded = armMenu, onDismissRequest = { armMenu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                    ArmFilter.entries.forEach { filter ->
                        DropdownMenuItem(text = { Text(filter.label) }, onClick = {
                            armMenu = false
                            actions.onUpdate { it.copy(armFilter = filter) }
                        })
                    }
                }
            }
            SwitchRow(
                title = "Salta le telecamere con batteria scarica",
                subtitle = "Ogni scatto consuma batteria sulle telecamere senza alimentazione",
                checked = s.skipLowBattery,
                onCheckedChange = { v -> actions.onUpdate { it.copy(skipLowBattery = v) } },
                enabled = s.enabled,
                icon = Icons.Outlined.BatteryAlert,
            )
            SwitchRow(
                title = "In background solo con Wi-Fi",
                subtitle = "Non usare la rete dati mobile del telefono",
                checked = s.unmeteredOnly,
                onCheckedChange = { v -> actions.onUpdate { it.copy(unmeteredOnly = v) } },
                enabled = s.enabled,
                icon = Icons.Outlined.Wifi,
            )
            SwitchRow(
                title = "Includi le nuove telecamere",
                subtitle = "Le telecamere non configurate partecipano all'aggiornamento",
                checked = s.includeNewCameras,
                onCheckedChange = { v -> actions.onUpdate { it.copy(includeNewCameras = v) } },
                enabled = s.enabled,
                icon = Icons.Outlined.NewReleases,
            )

            if (s.enabled) {
                StatusCard(state, now, ignoringBattery, onRunNow = actions.onRunNow, onRequestBattery = actions.onRequestBattery)
            }

            SectionHeader("Telecamere")
            if (state.cameras.isEmpty()) {
                Text(
                    "Caricamento telecamere…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.cameras.forEachIndexed { i, camera ->
                if (i > 0) ListDivider()
                CameraRefreshRow(
                    camera = camera,
                    settings = s,
                    attempts = state.attempts,
                    now = now,
                    onIncluded = { actions.onCameraIncluded(camera.key, it) },
                    onEditInterval = { dialog = SettingsDialog.CameraInterval(camera) },
                )
            }

            SectionHeader("Account")
            ValueRow(
                title = "Account Blink",
                subtitle = state.email,
                value = "Esci",
                onClick = { dialog = SettingsDialog.Logout },
                icon = Icons.AutoMirrored.Outlined.Logout,
            )

            SectionHeader("Informazioni")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(
                    "Blink Cam ${BuildConfig.VERSION_NAME}\n" +
                        "App non ufficiale, non affiliata a Blink/Amazon. Usa le stesse API dell'app ufficiale, " +
                        "che possono cambiare senza preavviso. La visione live non è disponibile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ValueRow(
                title = "Un progetto DaboPrint3D",
                subtitle = "Design & Printing · guarda il canale YouTube",
                value = "YouTube",
                onClick = actions.onOpenYoutube,
                icon = Icons.Outlined.SmartDisplay,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    when (val d = dialog) {
        SettingsDialog.DefaultInterval -> IntervalDialog(
            title = "Intervallo predefinito",
            current = s.defaultIntervalMinutes,
            defaultMinutes = null,
            onDismiss = { dialog = null },
            onConfirm = { v ->
                dialog = null
                if (v != null) actions.onUpdate { it.copy(defaultIntervalMinutes = v) }
            },
        )
        is SettingsDialog.CameraInterval -> IntervalDialog(
            title = "Intervallo per ${d.camera.name}",
            current = s.configFor(d.camera.key).intervalMinutes,
            defaultMinutes = s.defaultIntervalMinutes,
            onDismiss = { dialog = null },
            onConfirm = { v ->
                dialog = null
                actions.onCameraInterval(d.camera.key, v)
            },
        )
        SettingsDialog.WindowStart -> TimeOfDayDialog("Inizio fascia oraria", s.windowStartMinute, { dialog = null }) { v ->
            dialog = null
            actions.onUpdate { it.copy(windowStartMinute = v) }
        }
        SettingsDialog.WindowEnd -> TimeOfDayDialog("Fine fascia oraria", s.windowEndMinute, { dialog = null }) { v ->
            dialog = null
            actions.onUpdate { it.copy(windowEndMinute = v) }
        }
        SettingsDialog.Logout -> ConfirmDialog(
            title = "Uscire dall'account?",
            text = "L'aggiornamento automatico verrà sospeso e le miniature salvate sul telefono eliminate.",
            confirmLabel = "Esci",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                actions.onLogout()
            },
        )
        null -> Unit
    }
}

@Composable
private fun StatusCard(
    state: SettingsUiState,
    now: java.time.Instant,
    ignoringBattery: Boolean,
    onRunNow: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val s = state.settings
    val shortest = state.cameras.filter { s.isIncluded(it.key) }.minOfOrNull { s.intervalFor(it.key) } ?: s.defaultIntervalMinutes
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Stato", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            val last = state.lastRun
            if (last != null) {
                Text("Ultimo giro automatico: ${relativeTime(last.at, now)}", style = MaterialTheme.typography.bodyMedium)
                if (last.updated.isNotEmpty()) Text("Aggiornate: ${last.updated.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (last.unchanged.isNotEmpty()) Text("In attesa: ${last.unchanged.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (last.failed.isNotEmpty()) {
                    Text("Errori: ${last.failed.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (last.skipped.isNotEmpty()) Text("Saltate: ${last.skipped.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (last.notDue.isNotEmpty()) {
                    Text(
                        "Non ancora da aggiornare: " + last.notDue.joinToString { (name, at) -> "$name (${upcomingTime(at, now)})" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text("Nessun aggiornamento automatico eseguito da quando l'app è aperta", style = MaterialTheme.typography.bodyMedium)
            }
            state.nextRunAt?.let {
                Text("Prossimo controllo: ${upcomingTime(it, now)}", style = MaterialTheme.typography.bodyMedium)
            }
            if (shortest < 15 && !ignoringBattery) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Con intervalli brevi Android può ritardare gli aggiornamenti quando il telefono è inattivo. " +
                        "Per maggiore puntualità escludi l'app dall'ottimizzazione della batteria.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onRunNow, enabled = !state.runningNow) {
                    if (state.runningNow) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Aggiorna ora")
                }
                Spacer(Modifier.width(8.dp))
                if (!ignoringBattery) {
                    OutlinedButton(onClick = onRequestBattery) {
                        Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Batteria")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRefreshRow(
    camera: BlinkCamera,
    settings: AutoRefreshSettings,
    attempts: Map<String, java.time.Instant>,
    now: java.time.Instant,
    onIncluded: (Boolean) -> Unit,
    onEditInterval: () -> Unit,
) {
    val config = settings.configFor(camera.key)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = config.included, onClick = onEditInterval)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(camera.name, style = MaterialTheme.typography.bodyLarge)
            val interval = AutoRefreshSettings.formatInterval(settings.intervalFor(camera.key))
            val detail = when {
                !config.included -> "Esclusa"
                config.intervalMinutes == null -> "Ogni $interval (predefinito) · tocca per cambiare"
                else -> "Ogni $interval · tocca per cambiare"
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.enabled && config.included) {
                val next = AutoRefreshPlanner.nextDueAt(camera, settings, attempts, now)
                val status = when {
                    !camera.isOnline -> " · offline, verrà saltata"
                    settings.skipLowBattery && camera.isLowBattery -> " · batteria scarica, verrà saltata"
                    else -> ""
                }
                Text(
                    "Miniatura ${relativeTime(camera.thumbnailTime, now)} · prossima " +
                        (if (next.isAfter(now)) upcomingTime(next, now) else "al prossimo controllo") + status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Switch(checked = config.included, onCheckedChange = onIncluded)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}
