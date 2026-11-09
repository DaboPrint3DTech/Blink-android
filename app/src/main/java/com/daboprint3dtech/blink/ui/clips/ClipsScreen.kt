package com.daboprint3dtech.blink.ui.clips

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.data.api.Clip
import com.daboprint3dtech.blink.ui.camera.ClipRow
import com.daboprint3dtech.blink.ui.components.ConfirmDialog
import com.daboprint3dtech.blink.ui.components.ListDivider
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.components.shortDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipsScreen(onBack: () -> Unit, onPlayClip: (Long) -> Unit) {
    val vm = appViewModel { ClipsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<Clip?>(null) }
    var periodMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clip") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { periodMenu = true }) { Icon(Icons.Outlined.DateRange, contentDescription = "Periodo") }
                        DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                            listOf(1L to "Ultime 24 ore", 7L to "Ultimi 7 giorni", 30L to "Ultimi 30 giorni", 60L to "Ultimi 60 giorni")
                                .forEach { (days, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            periodMenu = false
                                            vm.load(days)
                                        },
                                    )
                                }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.clips.isNotEmpty(),
            onRefresh = { vm.load() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.cameraNames.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = state.cameraFilter == null, onClick = { vm.setFilter(null) }, label = { Text("Tutte") })
                        state.cameraNames.forEach { name ->
                            FilterChip(
                                selected = state.cameraFilter == name,
                                onClick = { vm.setFilter(if (state.cameraFilter == name) null else name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                when {
                    state.loading && state.clips.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null && state.clips.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.load() }, modifier = Modifier.padding(top = 12.dp)) { Text("Riprova") }
                    }
                    state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nessuna clip nel periodo selezionato", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.visible, key = { it.id }) { clip ->
                            ClipRow(clip, onPlayClip) {
                                IconButton(onClick = { toDelete = clip }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Elimina")
                                }
                            }
                            ListDivider()
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { clip ->
        ConfirmDialog(
            title = "Eliminare la clip?",
            text = "La clip di ${clip.deviceName} del ${shortDateTime(clip.createdAt)} verrà eliminata definitivamente dal cloud Blink.",
            confirmLabel = "Elimina",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                vm.delete(clip)
            },
        )
    }
}
