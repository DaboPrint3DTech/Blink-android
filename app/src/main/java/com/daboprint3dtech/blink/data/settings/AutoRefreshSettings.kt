package com.daboprint3dtech.blink.data.settings

import kotlinx.serialization.Serializable

/** Quando eseguire l'aggiornamento automatico rispetto allo stato del sistema. */
@Serializable
enum class ArmFilter(val label: String) {
    ALWAYS("Sempre"),
    ONLY_ARMED("Solo con sistema armato"),
    ONLY_DISARMED("Solo con sistema disarmato"),
}

/** Impostazioni della singola telecamera. */
@Serializable
data class CameraRefreshConfig(
    /** Se la telecamera partecipa all'aggiornamento automatico. */
    val included: Boolean = true,
    /** Intervallo specifico in minuti; null = usa l'intervallo predefinito. */
    val intervalMinutes: Int? = null,
)

/** Configurazione dell'aggiornamento automatico delle miniature. */
@Serializable
data class AutoRefreshSettings(
    val enabled: Boolean = false,
    /** Intervallo predefinito tra due miniature della stessa telecamera, in minuti. */
    val defaultIntervalMinutes: Int = 30,
    /** Impostazioni per telecamera, per chiave ([com.daboprint3dtech.blink.data.api.BlinkCamera.key]). */
    val cameras: Map<String, CameraRefreshConfig> = emptyMap(),
    /** Telecamere incluse se non configurate esplicitamente. */
    val includeNewCameras: Boolean = true,
    /** Limita l'aggiornamento a una fascia oraria. */
    val windowEnabled: Boolean = false,
    /** Inizio fascia, minuti dalla mezzanotte (ora locale). */
    val windowStartMinute: Int = 7 * 60,
    /** Fine fascia, minuti dalla mezzanotte; se < inizio la fascia scavalca la mezzanotte. */
    val windowEndMinute: Int = 23 * 60,
    val armFilter: ArmFilter = ArmFilter.ALWAYS,
    /** Non scattare con le telecamere che segnalano batteria scarica. */
    val skipLowBattery: Boolean = true,
    /** In background, esegui solo con rete non a consumo (Wi-Fi). */
    val unmeteredOnly: Boolean = false,
) {
    fun configFor(cameraKey: String): CameraRefreshConfig =
        cameras[cameraKey] ?: CameraRefreshConfig(included = includeNewCameras)

    fun isIncluded(cameraKey: String) = configFor(cameraKey).included

    fun intervalFor(cameraKey: String): Int =
        (configFor(cameraKey).intervalMinutes ?: defaultIntervalMinutes).coerceAtLeast(MIN_INTERVAL_MINUTES)

    fun withCamera(cameraKey: String, transform: (CameraRefreshConfig) -> CameraRefreshConfig) =
        copy(cameras = cameras + (cameraKey to transform(configFor(cameraKey))))

    companion object {
        const val MIN_INTERVAL_MINUTES = 1
        const val MAX_INTERVAL_MINUTES = 7 * 24 * 60

        /** Intervalli proposti nei menu (minuti). */
        val PRESET_INTERVALS = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 120, 180, 240, 360, 720, 1440)

        fun formatInterval(minutes: Int): String = when {
            minutes < 60 -> "$minutes min"
            minutes % 1440 == 0 -> (minutes / 1440).let { if (it == 1) "1 giorno" else "$it giorni" }
            minutes % 60 == 0 -> (minutes / 60).let { if (it == 1) "1 ora" else "$it ore" }
            else -> "${minutes / 60} h ${minutes % 60} min"
        }

        fun formatMinuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}
