package com.daboprint3dtech.blink.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.daboprint3dtech.blink.ui.components.appContainer
import com.daboprint3dtech.blink.ui.components.shortDateTime
import com.daboprint3dtech.blink.ui.components.userMessage

/** Riproduzione di una clip dal cloud Blink (serve l'header di autorizzazione). */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(clipId: Long, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val clip = remember(clipId) { container.repository.cachedClip(clipId) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(if (clip == null) "Clip non disponibile" else null) }
    // Il player nasce e muore con la schermata; gli header di autorizzazione si impostano prima di prepare().
    val dataSource = remember { DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true) }
    val player = remember {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = "Impossibile riprodurre la clip (${e.errorCodeName})"
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(clip) {
        if (clip == null) return@LaunchedEffect
        try {
            dataSource.setDefaultRequestProperties(container.repository.mediaHeaders())
            player.setMediaItem(MediaItem.fromUri(container.repository.clipVideoUrl(clip)))
            player.prepare()
            player.playWhenReady = true
            ready = true
        } catch (e: Exception) {
            error = e.userMessage()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        clip?.let { "${it.deviceName} · ${shortDateTime(it.createdAt)}" } ?: "Clip",
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black), contentAlignment = Alignment.Center) {
            val message = error
            when {
                message != null -> Text(message, color = MaterialTheme.colorScheme.error)
                !ready -> CircularProgressIndicator(color = Color.White)
                else -> AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
