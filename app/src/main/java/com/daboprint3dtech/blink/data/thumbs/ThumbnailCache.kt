package com.daboprint3dtech.blink.data.thumbs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cache su disco delle miniature: un file JPEG per telecamera + l'URL da cui è stato scaricato.
 * Così l'app mostra subito l'ultima immagine (anche offline) e il lavoro in background
 * può aggiornarla mentre la UI la osserva tramite [versions].
 */
class ThumbnailCache(private val dir: File) {

    private val _versions = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Versione (timestamp di scrittura) di ogni miniatura in cache, per chiave telecamera. */
    val versions: StateFlow<Map<String, Long>> = _versions.asStateFlow()

    init {
        dir.mkdirs()
        _versions.value = dir.listFiles { f -> f.extension == "jpg" }
            ?.associate { it.nameWithoutExtension to it.lastModified() }
            .orEmpty()
    }

    fun file(key: String): File = File(dir, "${safe(key)}.jpg")

    fun cachedUrl(key: String): String? = File(dir, "${safe(key)}.url").takeIf { it.exists() }?.readText()

    /** Salva l'immagine solo se è davvero un JPEG (evita di sovrascrivere con pagine d'errore). */
    suspend fun store(key: String, sourceUrl: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isJpeg(bytes)) return@withContext false
        val target = file(key)
        val tmp = File(dir, "${safe(key)}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        File(dir, "${safe(key)}.url").writeText(sourceUrl)
        val version = System.currentTimeMillis()
        target.setLastModified(version)
        _versions.update { it + (key to version) }
        true
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        _versions.value = emptyMap()
    }

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        fun isJpeg(bytes: ByteArray) =
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    }
}
