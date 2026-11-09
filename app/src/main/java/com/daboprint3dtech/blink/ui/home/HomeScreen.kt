package com.daboprint3dtech.blink.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.BlinkNetwork
import com.daboprint3dtech.blink.data.settings.AutoRefreshPlanner
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.ui.components.CameraThumbnail
import com.daboprint3dtech.blink.ui.components.ConfirmDialog
import com.daboprint3dtech.blink.ui.components.StatusChip
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.components.relativeTime
import com.daboprint3dtech.blink.ui.components.upcomingTime
import com.daboprint3dtech.blink.ui.theme.StatusColors
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant

/** Azioni della schermata principale. */
data class HomeActions(
    val onRefresh: () -> Unit = {},
    val onSnapshot: (BlinkCamera) -> Unit = {},
    val onSnapshotAll: () -> Unit = {},
    val onArm: (BlinkNetwork, Boolean) -> Unit = { _, _ -> },
    val onMotion: (BlinkCamera, Boolean) -> Unit = { _, _ -> },
    val onOpenCamera: (String) -> Unit = {},
    val onOpenClips: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun HomeScreen(
    onOpenCamera: (String) -> Unit,
    onOpenClips: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    HomeContent(
        state = state,
        snackbar = snackbar,
        thumbnailFile = vm.thumbnails::file,
        actions = HomeActions(
            onRefresh = vm::refresh,
            onSnapshot = vm::snapshot,
            onSnapshotAll = vm::snapshotAll,
            onArm = vm::setArmed,
            onMotion = vm::setMotion,
            onOpenCamera = onOpenCamera,
            onOpenClips = onOpenClips,
            onOpenSettings = onOpenSettings,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    thumbnailFile: (String) -> File,
    actions: HomeActions,
    now: Instant = rememberNow(),
) {
    var confirmSnapAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Le mie telecamere")
                        state.lastUpdated?.let {
                            Text(
                                "Aggiornato ${relativeTime(it, now)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { confirmSnapAll = true }, enabled = state.home?.cameras?.isNotEmpty() == true) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Aggiorna tutte le miniature")
                    }
                    IconButton(onClick = actions.onOpenClips) { Icon(Icons.Outlined.VideoLibrary, contentDescription = "Clip") }
                    IconButton(onClick = actions.onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Impostazioni") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.home != null,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val home = state.home
            val loadError = state.loadError
            when {
                home == null && loadError != null -> ErrorState(loadError, actions.onRefresh)
                home == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                home.cameras.isEmpty() && home.networks.isEmpty() -> ErrorState("Nessuna telecamera trovata sull'account", actions.onRefresh)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AutoRefreshBanner(state.settings, home.cameras, state, now, actions.onOpenSettings)
                    }
                    val networkIds = home.networks.map { it.id }.toSet()
                    for (network in home.networks) {
                        item(key = "net-${network.id}", span = { GridItemSpan(maxLineSpan) }) {
                            NetworkHeader(network, busy = network.id in state.busyNetworks, onArm = { actions.onArm(network, it) })
                        }
                        items(home.camerasOf(network.id), key = { it.key }) { camera ->
                            CameraCard(camera, state, now, thumbnailFile, actions)
                        }
                    }
                    // Dispositivi senza sistema associato (raro)
                    items(home.cameras.filter { it.networkId !in networkIds }, key = { it.key }) { camera ->
                        CameraCard(camera, state, now, thumbnailFile, actions)
                    }
                }
            }
        }
    }

    if (confirmSnapAll) {
        ConfirmDialog(
            title = "Aggiornare tutte le miniature?",
            text = "Ogni telecamera scatterà una nuova immagine. Sulle telecamere a batteria ogni scatto consuma un po' di batteria.",
            confirmLabel = "Aggiorna",
            onDismiss = { confirmSnapAll = false },
            onConfirm = {
                confirmSnapAll = false
                actions.onSnapshotAll()
            },
        )
    }
}

/** Istante corrente che si aggiorna ogni 30 secondi (per i tempi relativi). */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    return now
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
private fun AutoRefreshBanner(
    settings: AutoRefreshSettings,
    cameras: List<BlinkCamera>,
    state: HomeUiState,
    now: Instant,
    onOpenSettings: () -> Unit,
) {
    val included = cameras.filter { settings.isIncluded(it.key) }
    val next = included.minOfOrNull { AutoRefreshPlanner.nextDueAt(it, settings, state.attempts, now) }
    Card(
        onClick = onOpenSettings,
        colors = CardDefaults.cardColors(
            containerColor = if (settings.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Autorenew, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (settings.enabled) {
                    Text("Aggiornamento automatico attivo", style = MaterialTheme.typography.titleSmall)
                    val nextText = if (next != null && next.isAfter(now)) upcomingTime(next, now) else "a breve"
                    Text(
                        "${included.size} telecamere · predefinito ogni ${AutoRefreshSettings.formatInterval(settings.defaultIntervalMinutes)}" +
                            " · prossimo $nextText",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("Aggiornamento automatico disattivato", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Tocca per aggiornare le miniature a intervalli che scegli tu",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkHeader(network: BlinkNetwork, busy: Boolean, onArm: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (network.armed) StatusColors.Armed else StatusColors.Disarmed,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(network.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sync = network.syncModuleStatus?.let { if (it == "online") " · Sync Module online" else " · Sync Module $it" }.orEmpty()
                Text(
                    (if (network.armed) "Armato" else "Disarmato") + sync,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Switch(checked = network.armed, onCheckedChange = onArm, enabled = !busy)
        }
    }
}

@Composable
private fun CameraCard(
    camera: BlinkCamera,
    state: HomeUiState,
    now: Instant,
    thumbnailFile: (String) -> File,
    actions: HomeActions,
) {
    val snapping = camera.key in state.snapshots
    val autoIncluded = state.settings.enabled && state.settings.isIncluded(camera.key)
    Card(onClick = { actions.onOpenCamera(camera.key) }, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            CameraThumbnail(
                file = thumbnailFile(camera.key),
                version = state.thumbnailVersions[camera.key],
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!camera.isOnline) StatusChip("Offline", StatusColors.Error, icon = Icons.Outlined.CloudOff)
                if (camera.isLowBattery) StatusChip("Batteria scarica", StatusColors.Warning, icon = Icons.Outlined.BatteryAlert)
            }
            if (autoIncluded) {
                StatusChip(
                    AutoRefreshSettings.formatInterval(state.settings.intervalFor(camera.key)),
                    Color.Black.copy(alpha = 0.6f),
                    icon = Icons.Outlined.Autorenew,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(camera.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Miniatura: ${relativeTime(camera.thumbnailTime, now)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (snapping) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { actions.onSnapshot(camera) }, enabled = !snapping && camera.isOnline) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Aggiorna miniatura")
            }
            Spacer(Modifier.weight(1f))
            Text("Movimento", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            val busy = camera.key in state.busyCameras
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Switch(checked = camera.motionEnabled, onCheckedChange = { actions.onMotion(camera, it) }, enabled = !busy)
        }
    }
}
