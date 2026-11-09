package com.daboprint3dtech.blink.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.daboprint3dtech.blink.data.BlinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

/** Carica (e tiene in memoria) le immagini remote autenticate, es. anteprime delle clip. */
class RemoteImageLoader(private val repository: BlinkRepository) {
    suspend fun load(url: String): Bitmap? {
        bitmapCache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { decode(repository.download(url)) }.getOrNull()
        }?.also { bitmapCache.put(url, it) }
    }

    fun clear() = bitmapCache.evictAll()
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * Miniatura di una telecamera dalla cache su disco. [version] cambia ogni volta che il file
 * viene aggiornato (anche dal lavoro in background): l'immagine precedente resta visibile
 * finché quella nuova non è pronta.
 */
@Composable
fun CameraThumbnail(
    file: File,
    version: Long?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val cacheKey = "${file.path}@$version"
    var image by remember(file.path) { mutableStateOf(bitmapCache.get(cacheKey)?.asImageBitmap()) }
    LaunchedEffect(cacheKey) {
        if (version == null) return@LaunchedEffect
        val bitmap = bitmapCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            if (file.exists()) runCatching { decode(file.readBytes()) }.getOrNull() else null
        }?.also { bitmapCache.put(cacheKey, it) }
        if (bitmap != null) image = bitmap.asImageBitmap()
    }
    BitmapOrPlaceholder(image, modifier, contentScale)
}

/** Immagine remota autenticata (anteprima clip). */
@Composable
fun RemoteImage(
    url: String?,
    loader: RemoteImageLoader,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var image by remember(url) { mutableStateOf(url?.let { bitmapCache.get(it)?.asImageBitmap() }) }
    LaunchedEffect(url) {
        if (url != null && image == null) image = loader.load(url)?.asImageBitmap()
    }
    BitmapOrPlaceholder(image, modifier, contentScale)
}

@Composable
private fun BitmapOrPlaceholder(image: ImageBitmap?, modifier: Modifier, contentScale: ContentScale) {
    Crossfade(targetState = image, label = "thumbnail", modifier = modifier) { bitmap ->
        if (bitmap == null) {
            Placeholder(Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
