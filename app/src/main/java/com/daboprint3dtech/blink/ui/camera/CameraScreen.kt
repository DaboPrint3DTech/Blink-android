package com.daboprint3dtech.blink.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.Clip
import com.daboprint3dtech.blink.data.api.DeviceKind
import com.daboprint3dtech.blink.data.settings.AutoRefreshPlanner
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.ui.components.CameraThumbnail
import com.daboprint3dtech.blink.ui.components.IntervalDialog
import com.daboprint3dtech.blink.ui.components.ListDivider
import com.daboprint3dtech.blink.ui.components.RemoteImage
import com.daboprint3dtech.blink.ui.components.SectionHeader
import com.daboprint3dtech.blink.ui.components.SwitchRow
import com.daboprint3dtech.blink.ui.components.ValueRow
import com.daboprint3dtech.blink.ui.components.appContainer
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.components.fullDateTime
import com.daboprint3dtech.blink.ui.components.relativeTime
import com.daboprint3dtech.blink.ui.components.shortDateTime
import com.daboprint3dtech.blink.ui.components.upcomingTime
import com.daboprint3dtech.blink.ui.home.rememberNow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(cameraKey: String, onBack: () -> Unit, onPlayClip: (Long) -> Unit) {
    val vm = appViewModel(key = "camera-$cameraKey") { CameraViewModel(it, cameraKey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showInterval by remember { mutableStateOf(false) }
    val now = rememberNow()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.camera?.name ?: "Telecamera") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val camera = state.camera
        if (camera == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ZoomableThumbnail(vm, state)
            Text(
                "Miniatura del ${fullDateTime(camera.thumbnailTime)} (${relativeTime(camera.thumbnailTime, now)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = vm::snapshot,
                    enabled = !state.snapshotting && camera.isOnline,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nuova miniatura")
                }
                OutlinedButton(
                    onClick = vm::recordClip,
                    enabled = !state.busy && camera.isOnline,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FiberManualRecord, contentDescription = null, tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(8.dp))
                    Text("Registra clip")
                }
            }

            SwitchRow(
                title = "Rilevamento movimento",
                subtitle = if (camera.kind == DeviceKind.CAMERA) "Attivo quando il sistema è armato" else null,
                checked = camera.motionEnabled,
                onCheckedChange = vm::setMotion,
                enabled = !state.busy,
                icon = Icons.AutoMirrored.Outlined.DirectionsRun,
            )

            AutoRefreshSection(camera, state, now, onEditInterval = { showInterval = true }, vm = vm)
            InfoSection(camera, state)
            ClipsSection(state, onPlayClip, vm::loadClips)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showInterval) {
        val camera = state.camera
        IntervalDialog(
            title = "Intervallo per ${camera?.name.orEmpty()}",
            current = camera?.let { state.settings.configFor(it.key).intervalMinutes },
            defaultMinutes = state.settings.defaultIntervalMinutes,
            onDismiss = { showInterval = false },
            onConfirm = {
                showInterval = false
                vm.setAutoInterval(it)
            },
        )
    }
}

@Composable
private fun ZoomableThumbnail(vm: CameraViewModel, state: CameraUiState) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clip(RoundedCornerShape(0.dp))
            .transformable(transform),
    ) {
        CameraThumbnail(
            file = vm.thumbnailFile,
            version = state.thumbnailVersion,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        if (state.snapshotting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Scatto in corso…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AutoRefreshSection(
    camera: BlinkCamera,
    state: CameraUiState,
    now: java.time.Instant,
    onEditInterval: () -> Unit,
    vm: CameraViewModel,
) {
    val settings = state.settings
    val config = settings.configFor(camera.key)
    SectionHeader("Aggiornamento automatico miniatura")
    if (!settings.enabled) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "L'aggiornamento automatico è disattivato.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::enableAutoRefresh) { Text("Attiva") }
        }
    }
    SwitchRow(
        title = "Includi questa telecamera",
        checked = config.included,
        onCheckedChange = vm::setAutoIncluded,
        icon = Icons.Outlined.Autorenew,
    )
    ValueRow(
        title = "Intervallo",
        subtitle = if (config.intervalMinutes == null) "Usa l'intervallo predefinito" else "Intervallo specifico per questa telecamera",
        value = AutoRefreshSettings.formatInterval(settings.intervalFor(camera.key)),
        onClick = onEditInterval,
        enabled = config.included,
        icon = Icons.Outlined.Schedule,
    )
    if (settings.enabled && config.included) {
        val next = AutoRefreshPlanner.nextDueAt(camera, settings, state.attempts)
        val nextText = if (next.isAfter(now)) upcomingTime(next, now) else "al prossimo controllo"
        val extra = when {
            !camera.isOnline -> " (telecamera offline: verrà saltata)"
            settings.skipLowBattery && camera.isLowBattery -> " (batteria scarica: verrà saltata)"
            else -> ""
        }
        Text(
            "Prossimo aggiornamento: $nextText$extra",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InfoSection(camera: BlinkCamera, state: CameraUiState) {
    SectionHeader("Informazioni")
    InfoRow("Sistema", state.network?.name ?: "—")
    InfoRow("Stato", if (camera.isOnline) "Online" else "Offline")
    camera.batteryState?.let { battery ->
        val level = camera.batteryLevel?.let { " (livello $it/3)" }.orEmpty()
        InfoRow("Batteria", (if (battery.equals("low", true)) "Scarica" else "OK") + level)
    }
    camera.wifiSignal?.let { InfoRow("Segnale Wi-Fi", "$it/5") }
    camera.syncSignal?.let { InfoRow("Segnale Sync Module", "$it/5") }
    camera.temperatureC?.let { InfoRow("Temperatura", "%.1f °C".format(it)) }
    InfoRow(
        "Tipo",
        when (camera.kind) {
            DeviceKind.CAMERA -> "Telecamera"
            DeviceKind.MINI -> "Blink Mini"
            DeviceKind.DOORBELL -> "Video Doorbell"
        } + camera.productType?.let { " ($it)" }.orEmpty(),
    )
    camera.firmware?.let { InfoRow("Firmware", it) }
    camera.serial?.let { InfoRow("Numero di serie", it) }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ClipsSection(state: CameraUiState, onPlayClip: (Long) -> Unit, onRetry: () -> Unit) {
    SectionHeader("Clip recenti (14 giorni)")
    when {
        state.clipsLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
        state.clipsError != null -> Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.clipsError, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Riprova") }
        }
        state.clips.isEmpty() -> Text(
            "Nessuna clip negli ultimi 14 giorni",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        else -> state.clips.forEachIndexed { i, clip ->
            if (i > 0) ListDivider()
            ClipRow(clip, onPlayClip)
        }
    }
}

@Composable
fun ClipRow(clip: Clip, onPlayClip: (Long) -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val container = appContainer()
    var thumbUrl by remember(clip.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(clip.id) { thumbUrl = runCatching { container.repository.clipThumbnailUrl(clip) }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().clickable { onPlayClip(clip.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = thumbUrl,
            loader = container.imageLoader,
            modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(clip.deviceName.ifBlank { "Clip" }, style = MaterialTheme.typography.titleSmall)
            Text(shortDateTime(clip.createdAt), style = MaterialTheme.typography.bodyMedium)
            val source = when (clip.source) {
                "pir" -> "Movimento"
                "liveview" -> "Visione live"
                "snapshot" -> "Manuale"
                null -> null
                else -> clip.source
            }
            if (source != null || !clip.watched) {
                Text(
                    listOfNotNull(source, if (!clip.watched) "Nuova" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        trailing?.invoke()
    }
}
