package com.daboprint3dtech.blink.data

import com.daboprint3dtech.blink.data.api.BlinkApi
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.Clip
import com.daboprint3dtech.blink.data.api.Homescreen
import com.daboprint3dtech.blink.data.thumbs.ThumbnailCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant

/** Esito dell'aggiornamento di una miniatura. */
sealed interface SnapshotResult {
    data object Updated : SnapshotResult
    data object NotChanged : SnapshotResult
    data class Failed(val error: Throwable) : SnapshotResult
}

/**
 * Punto unico di accesso ai dati Blink per UI e lavoro in background.
 * Mantiene in memoria l'ultimo stato (homescreen) e sincronizza le miniature su disco.
 */
class BlinkRepository(
    private val api: BlinkApi,
    val thumbnails: ThumbnailCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val thumbnailSettleDelayMs: Long = 3_000,
) {
    private val _homescreen = MutableStateFlow<Homescreen?>(null)
    val homescreen: StateFlow<Homescreen?> = _homescreen.asStateFlow()

    private val _snapshotsInProgress = MutableStateFlow<Set<String>>(emptySet())

    /** Telecamere (chiavi) per cui è in corso lo scatto di una nuova miniatura. */
    val snapshotsInProgress: StateFlow<Set<String>> = _snapshotsInProgress.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Instant?>(null)
    val lastUpdated: StateFlow<Instant?> = _lastUpdated.asStateFlow()

    private val refreshMutex = Mutex()
    private val commandMutex = Mutex()
    private val downloadPermits = Semaphore(3)
    private var fetchedAt = 0L
    private val clipCache = mutableMapOf<Long, Clip>()

    /**
     * Aggiorna lo stato da Blink. Se i dati in memoria hanno meno di [maxAge] li riusa
     * (evita chiamate ravvicinate, Blink limita la frequenza delle richieste).
     */
    suspend fun refresh(maxAge: Duration = Duration.ZERO): Homescreen = refreshMutex.withLock {
        val cached = _homescreen.value
        if (cached != null && clock() - fetchedAt < maxAge.toMillis()) return cached
        val home = api.homescreen()
        _homescreen.value = home
        fetchedAt = clock()
        _lastUpdated.value = Instant.ofEpochMilli(fetchedAt)
        syncThumbnails(home)
        home
    }

    private suspend fun syncThumbnails(home: Homescreen) = coroutineScope {
        home.cameras.map { camera -> async { downloadPermits.withPermit { syncThumbnail(camera) } } }.awaitAll()
    }

    /** Scarica la miniatura se l'URL è cambiato rispetto a quella in cache. */
    private suspend fun syncThumbnail(camera: BlinkCamera): Boolean {
        val url = runCatching { api.thumbnailUrl(camera) }.getOrNull() ?: return false
        if (thumbnails.cachedUrl(camera.key) == url && thumbnails.file(camera.key).exists()) return false
        val bytes = runCatching { api.download(url) }.getOrNull() ?: return false
        return thumbnails.store(camera.key, url, bytes)
    }

    /** Scatta una nuova miniatura per una telecamera (pulsante "aggiorna" dell'app). */
    suspend fun snapshot(camera: BlinkCamera): SnapshotResult = snapshot(listOf(camera)).getValue(camera.key)

    /**
     * Scatta nuove miniature per più telecamere, una alla volta (lo stesso Sync Module
     * accetta un comando per volta), poi ricarica lo stato e scarica le nuove immagini.
     */
    suspend fun snapshot(cameras: List<BlinkCamera>): Map<String, SnapshotResult> {
        if (cameras.isEmpty()) return emptyMap()
        val keys = cameras.map { it.key }.toSet()
        _snapshotsInProgress.update { it + keys }
        try {
            val before = cameras.associate { it.key to it.thumbnailPath }
            val results = mutableMapOf<String, SnapshotResult>()
            commandMutex.withLock {
                for (camera in cameras) {
                    runCatching { api.requestThumbnail(camera) }
                        .onFailure { results[camera.key] = SnapshotResult.Failed(it) }
                }
            }
            val pending = keys - results.keys
            // Dopo il completamento del comando la miniatura nuova può comparire con qualche secondo di ritardo.
            var changed = emptySet<String>()
            for (attempt in 0 until 3) {
                if (attempt > 0) delay(thumbnailSettleDelayMs)
                val home = runCatching { refresh() }.getOrNull() ?: continue
                changed = pending.filter { key -> home.camera(key)?.thumbnailPath != before[key] }.toSet()
                if (changed.size == pending.size) break
            }
            return finish(results, pending, changed)
        } finally {
            _snapshotsInProgress.update { it - keys }
        }
    }

    private fun finish(
        results: MutableMap<String, SnapshotResult>,
        pending: Set<String>,
        changed: Set<String>,
    ): Map<String, SnapshotResult> {
        for (key in pending) results[key] = if (key in changed) SnapshotResult.Updated else SnapshotResult.NotChanged
        return results
    }

    suspend fun setSystemArmed(networkId: Long, armed: Boolean) {
        commandMutex.withLock { api.setSystemArmed(networkId, armed) }
        _homescreen.update { home ->
            home?.copy(networks = home.networks.map { if (it.id == networkId) it.copy(armed = armed) else it })
        }
        runCatching { refresh() }
    }

    suspend fun setMotionDetection(camera: BlinkCamera, enabled: Boolean) {
        commandMutex.withLock { api.setMotionDetection(camera, enabled) }
        _homescreen.update { home ->
            home?.copy(cameras = home.cameras.map { if (it.key == camera.key) it.copy(motionEnabled = enabled) else it })
        }
        runCatching { refresh() }
    }

    suspend fun recordClip(camera: BlinkCamera) {
        commandMutex.withLock { api.requestClip(camera) }
    }

    /** Clip degli ultimi [days] giorni, dalla più recente. */
    suspend fun loadClips(days: Long = 30, maxPages: Int = 6): List<Clip> {
        val since = Instant.ofEpochMilli(clock()).minus(Duration.ofDays(days))
        val all = mutableListOf<Clip>()
        for (page in 1..maxPages) {
            val items = api.clips(since, page)
            if (items.isEmpty()) break
            all += items
        }
        val sorted = all.distinctBy { it.id }.sortedByDescending { it.createdAt }
        synchronized(clipCache) {
            clipCache.clear()
            sorted.forEach { clipCache[it.id] = it }
        }
        return sorted
    }

    fun cachedClip(id: Long): Clip? = synchronized(clipCache) { clipCache[id] }

    suspend fun deleteClip(clip: Clip) {
        api.deleteClips(listOf(clip.id))
        synchronized(clipCache) { clipCache.remove(clip.id) }
    }

    suspend fun clipVideoUrl(clip: Clip) = api.clipVideoUrl(clip)
    suspend fun clipThumbnailUrl(clip: Clip) = api.clipThumbnailUrl(clip)
    suspend fun mediaHeaders() = api.mediaHeaders()
    suspend fun download(url: String) = api.download(url)

    fun clear() {
        _homescreen.value = null
        _lastUpdated.value = null
        fetchedAt = 0
        synchronized(clipCache) { clipCache.clear() }
        thumbnails.clear()
    }
}
