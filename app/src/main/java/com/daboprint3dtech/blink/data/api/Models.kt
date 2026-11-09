package com.daboprint3dtech.blink.data.api

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Famiglia di dispositivo: determina gli endpoint da usare per i comandi. */
enum class DeviceKind {
    /** Telecamere collegate al Sync Module (Outdoor, Indoor, XT, XT2...). */
    CAMERA,

    /** Blink Mini / Mini 2 ("owl"). */
    MINI,

    /** Video Doorbell ("lotus"). */
    DOORBELL,
}

/** Un "sistema" Blink (rete di telecamere, di solito con un Sync Module). */
data class BlinkNetwork(
    val id: Long,
    val name: String,
    val armed: Boolean,
    val syncModuleStatus: String?,
)

data class BlinkCamera(
    val id: Long,
    val networkId: Long,
    val name: String,
    val kind: DeviceKind,
    /** Tipo prodotto Blink ("catalina", "sedona", "owl", "lotus", ...). */
    val productType: String?,
    /** Rilevamento movimento attivo sulla singola telecamera. */
    val motionEnabled: Boolean,
    val status: String?,
    /** "ok" / "low" per i modelli a batteria, null per quelli alimentati. */
    val batteryState: String?,
    /** Livello batteria 0-3 (se disponibile). */
    val batteryLevel: Int?,
    /** Segnale Wi-Fi 0-5. */
    val wifiSignal: Int?,
    /** Segnale verso il Sync Module 0-5. */
    val syncSignal: Int?,
    val temperatureF: Int?,
    val thumbnailPath: String?,
    val serial: String?,
    val firmware: String?,
) {
    /** Chiave univoca e stabile, usata per cache e impostazioni. */
    val key: String get() = "${kind.name.lowercase(Locale.ROOT)}-$id"

    val isOnline: Boolean get() = status == null || status.lowercase(Locale.ROOT) in ONLINE_STATES

    val isLowBattery: Boolean get() = batteryState?.lowercase(Locale.ROOT) == "low"

    val temperatureC: Double?
        get() = temperatureF?.let { Math.round((it - 32) / 1.8 * 10) / 10.0 }

    /** Momento in cui è stata acquisita la miniatura attuale (se ricavabile dall'URL). */
    val thumbnailTime: Instant? get() = BlinkUrls.thumbnailTimestamp(thumbnailPath)

    companion object {
        private val ONLINE_STATES = setOf("online", "done")
    }
}

data class Homescreen(
    val networks: List<BlinkNetwork>,
    val cameras: List<BlinkCamera>,
) {
    fun camerasOf(networkId: Long) = cameras.filter { it.networkId == networkId }
    fun camera(key: String) = cameras.firstOrNull { it.key == key }
    fun network(id: Long) = networks.firstOrNull { it.id == id }

    companion object {
        fun parse(json: JsonObject): Homescreen {
            val syncStatus = json.array("sync_modules").mapNotNull { sm ->
                sm.long("network_id")?.let { it to sm.str("status") }
            }.toMap()
            val networks = json.array("networks").mapNotNull { n ->
                val id = n.long("id") ?: return@mapNotNull null
                BlinkNetwork(
                    id = id,
                    name = n.str("name") ?: "Sistema $id",
                    armed = n.bool("armed") ?: false,
                    syncModuleStatus = syncStatus[id],
                )
            }
            val cameras = json.array("cameras").mapNotNull { parseCamera(it, DeviceKind.CAMERA) } +
                json.array("owls").mapNotNull { parseCamera(it, DeviceKind.MINI) } +
                json.array("doorbells").mapNotNull { parseCamera(it, DeviceKind.DOORBELL) }
            return Homescreen(networks, cameras)
        }

        private fun parseCamera(c: JsonObject, kind: DeviceKind): BlinkCamera? {
            val id = c.long("id") ?: return null
            val networkId = c.long("network_id") ?: return null
            val signals = c.obj("signals")
            return BlinkCamera(
                id = id,
                networkId = networkId,
                name = c.str("name") ?: "Telecamera $id",
                kind = kind,
                productType = c.str("type"),
                motionEnabled = c.bool("enabled") ?: false,
                status = c.str("status"),
                batteryState = c.str("battery_state") ?: c.str("battery"),
                batteryLevel = signals?.int("battery"),
                wifiSignal = signals?.int("wifi") ?: c.int("wifi_strength"),
                syncSignal = signals?.int("lfr"),
                temperatureF = signals?.int("temp") ?: c.int("temperature"),
                thumbnailPath = c.str("thumbnail"),
                serial = c.str("serial"),
                firmware = c.str("fw_version"),
            )
        }
    }
}

/** Un video registrato (clip) presente nel cloud Blink. */
data class Clip(
    val id: Long,
    val createdAt: Instant?,
    val deviceId: Long?,
    val deviceName: String,
    val networkId: Long?,
    val networkName: String?,
    val source: String?,
    val watched: Boolean,
    val thumbnailPath: String?,
    val mediaPath: String,
) {
    companion object {
        fun parseList(json: JsonObject): List<Clip> = json.array("media").mapNotNull { m ->
            if (m.bool("deleted") == true) return@mapNotNull null
            val id = m.long("id") ?: return@mapNotNull null
            val media = m.str("media") ?: return@mapNotNull null
            Clip(
                id = id,
                createdAt = m.str("created_at")?.let(BlinkUrls::parseInstant),
                deviceId = m.long("device_id"),
                deviceName = m.str("device_name") ?: "",
                networkId = m.long("network_id"),
                networkName = m.str("network_name"),
                source = m.str("source"),
                watched = m.bool("watched") ?: false,
                thumbnailPath = m.str("thumbnail"),
                mediaPath = media,
            )
        }
    }
}

/** Costruzione degli URL dei media (porting della logica di blinkpy / homebridge-blink). */
object BlinkUrls {
    /**
     * URL assoluto della miniatura di una telecamera.
     * - API nuova: il campo contiene solo il timestamp → si costruisce l'URL v3
     * - API vecchia: percorso senza estensione → si aggiunge ".jpg"
     */
    fun thumbnailUrl(baseUrl: String, accountId: Long, camera: BlinkCamera): String? {
        val t = camera.thumbnailPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.all(Char::isDigit)) {
            val type = camera.productType ?: "catalina"
            return "$baseUrl/api/v3/media/accounts/$accountId/networks/${camera.networkId}/" +
                "$type/${camera.id}/thumbnail/thumbnail.jpg?ts=$t&ext="
        }
        return absolute(baseUrl, withJpg(t))
    }

    fun clipThumbnailUrl(baseUrl: String, clip: Clip): String? =
        clip.thumbnailPath?.takeIf { it.isNotBlank() }?.let { absolute(baseUrl, withJpg(it)) }

    fun clipVideoUrl(baseUrl: String, clip: Clip): String = absolute(baseUrl, clip.mediaPath)

    private fun withJpg(path: String) = if (path.contains(".jpg") || path.endsWith("&ext=")) path else "$path.jpg"

    private fun absolute(baseUrl: String, path: String) =
        if (path.startsWith("http://") || path.startsWith("https://")) path
        else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private val tsRegex = Regex("[?&]ts=(\\d+)")
    private val legacyRegex = Regex("(\\d{4})_(\\d{2})_(\\d{2})__(\\d{2})_(\\d{2})(AM|PM)", RegexOption.IGNORE_CASE)

    /** Ricava l'istante di acquisizione dalla stringa della miniatura (se possibile). */
    fun thumbnailTimestamp(thumbnail: String?): Instant? {
        val t = thumbnail?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val epoch = when {
            t.all(Char::isDigit) -> t.toLongOrNull()
            else -> tsRegex.find(t)?.groupValues?.get(1)?.toLongOrNull()
        }
        if (epoch != null) {
            return if (epoch > 100_000_000_000L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        }
        val m = legacyRegex.find(t) ?: return null
        val (y, mo, d, h, mi, ampm) = m.destructured
        var hour = h.toInt() % 12
        if (ampm.equals("PM", ignoreCase = true)) hour += 12
        return runCatching {
            LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), hour, mi.toInt()).toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    fun parseInstant(value: String): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(value) }.getOrNull()
}
