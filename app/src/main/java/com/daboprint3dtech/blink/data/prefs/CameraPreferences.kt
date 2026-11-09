package com.daboprint3dtech.blink.data.prefs

import com.daboprint3dtech.blink.data.api.BlinkCamera
import kotlinx.serialization.Serializable

/**
 * Preferenze dell'utente su come mostrare le telecamere: ordine personalizzato,
 * preferite e avviso di batteria scarica.
 */
@Serializable
data class CameraPreferences(
    /** Chiavi telecamera nell'ordine scelto dall'utente; quelle non elencate vanno in fondo. */
    val order: List<String> = emptyList(),
    /** Telecamere contrassegnate come preferite. */
    val favorites: Set<String> = emptySet(),
    /** Mostra prima le preferite nella schermata principale. */
    val favoritesFirst: Boolean = true,
    /** Avvisa (notifica) quando una telecamera scende sotto la soglia di batteria. */
    val batteryAlertEnabled: Boolean = false,
    /** Soglia del livello batteria (0-3) sotto o uguale alla quale scatta l'avviso. */
    val batteryAlertLevel: Int = 1,
) {
    fun isFavorite(key: String) = key in favorites

    fun toggleFavorite(key: String) =
        copy(favorites = if (key in favorites) favorites - key else favorites + key)

    companion object {
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 3
    }
}

/** Logica pura (testabile) di ordinamento e stato batteria. */
object CameraOrdering {

    /**
     * Ordina le telecamere secondo le preferenze: prima l'ordine scelto dall'utente,
     * poi le altre nell'ordine di arrivo; se [CameraPreferences.favoritesFirst] è attivo,
     * le preferite vengono comunque portate in testa mantenendo l'ordine relativo.
     */
    fun ordered(cameras: List<BlinkCamera>, prefs: CameraPreferences): List<BlinkCamera> {
        val rank = prefs.order.withIndex().associate { (i, key) -> key to i }
        val byOrder = cameras.sortedBy { rank[it.key] ?: (prefs.order.size + cameras.indexOf(it)) }
        if (!prefs.favoritesFirst || prefs.favorites.isEmpty()) return byOrder
        val (fav, rest) = byOrder.partition { prefs.isFavorite(it.key) }
        return fav + rest
    }

    /** Ordine completo e stabile delle chiavi (usato dalla schermata di riordino). */
    fun orderedKeys(cameras: List<BlinkCamera>, prefs: CameraPreferences): List<String> =
        ordered(cameras, prefs).map { it.key }

    /** Sposta la telecamera [key] di una posizione; restituisce il nuovo ordine completo. */
    fun move(currentOrder: List<String>, key: String, up: Boolean): List<String> {
        val list = currentOrder.toMutableList()
        val i = list.indexOf(key)
        if (i < 0) return currentOrder
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= list.size) return currentOrder
        list[i] = list[j].also { list[j] = list[i] }
        return list
    }
}

/** Stato batteria di una telecamera, per l'avviso e la vista di riepilogo. */
object BatteryStatus {

    /** True se la telecamera ha una batteria (i modelli alimentati non la riportano). */
    fun hasBattery(camera: BlinkCamera) =
        camera.batteryState != null || camera.batteryLevel != null

    /** True se la telecamera è sotto o uguale alla soglia di avviso. */
    fun isLow(camera: BlinkCamera, prefs: CameraPreferences): Boolean {
        if (!hasBattery(camera)) return false
        if (camera.isLowBattery) return true
        val level = camera.batteryLevel ?: return false
        return level <= prefs.batteryAlertLevel
    }

    /** Telecamere attualmente sotto soglia (solo quelle con batteria). */
    fun lowCameras(cameras: List<BlinkCamera>, prefs: CameraPreferences): List<BlinkCamera> =
        cameras.filter { isLow(it, prefs) }
}
