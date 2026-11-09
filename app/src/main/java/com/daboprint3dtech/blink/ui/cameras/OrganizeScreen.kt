package com.daboprint3dtech.blink.ui.cameras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.ui.components.SwitchRow
import com.daboprint3dtech.blink.ui.components.appViewModel
import com.daboprint3dtech.blink.ui.theme.StatusColors

/** Azioni della schermata di organizzazione. */
data class OrganizeActions(
    val onBack: () -> Unit = {},
    val onUp: (BlinkCamera) -> Unit = {},
    val onDown: (BlinkCamera) -> Unit = {},
    val onToggleFavorite: (BlinkCamera) -> Unit = {},
    val onFavoritesFirst: (Boolean) -> Unit = {},
)

@Composable
fun OrganizeScreen(onBack: () -> Unit) {
    val vm = appViewModel { OrganizeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    OrganizeContent(
        state,
        OrganizeActions(
            onBack = onBack,
            onUp = vm::moveUp,
            onDown = vm::moveDown,
            onToggleFavorite = vm::toggleFavorite,
            onFavoritesFirst = vm::setFavoritesFirst,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeContent(state: OrganizeUiState, actions: OrganizeActions) {
    val ordered = state.ordered

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organizza telecamere") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                SwitchRow(
                    title = "Mostra prima le preferite",
                    subtitle = "Le telecamere con la stella compaiono in cima alla schermata principale",
                    checked = state.prefs.favoritesFirst,
                    onCheckedChange = actions.onFavoritesFirst,
                )
                HorizontalDivider()
                Text(
                    "Usa le frecce per cambiare l'ordine e la stella per i preferiti.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (ordered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Caricamento telecamere…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(ordered, key = { _, c -> c.key }) { index, camera ->
                OrganizeRow(
                    camera = camera,
                    favorite = state.prefs.isFavorite(camera.key),
                    isFirst = index == 0,
                    isLast = index == ordered.lastIndex,
                    onUp = { actions.onUp(camera) },
                    onDown = { actions.onDown(camera) },
                    onToggleFavorite = { actions.onToggleFavorite(camera) },
                )
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun OrganizeRow(
    camera: BlinkCamera,
    favorite: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                tint = if (favorite) StatusColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(camera.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (camera.isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUp, enabled = !isFirst) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Sposta su")
        }
        IconButton(onClick = onDown, enabled = !isLast) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sposta giù")
        }
    }
}
