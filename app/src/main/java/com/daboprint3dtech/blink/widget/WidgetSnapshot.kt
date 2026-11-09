package com.daboprint3dtech.blink.widget

import android.content.Context
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.prefs.BatteryStatus
import com.daboprint3dtech.blink.data.prefs.CameraOrdering
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Dati minimi, salvati su disco, che il widget mostra anche se il processo dell'app è stato chiuso. */
@Serializable
data class WidgetCamera(
    val key: String,
    val name: String,
    val online: Boolean,
    val lowBattery: Boolean,
    /** Istante dell'ultima miniatura (epoch millis) per l'etichetta "x min fa". */
    val thumbnailMillis: Long?,
)

@Serializable
data class WidgetSnapshot(
    val loggedIn: Boolean = false,
    val cameras: List<WidgetCamera> = emptyList(),
    val updatedAtMillis: Long = 0,
)

/** Legge/scrive lo snapshot del widget in un file privato dell'app. */
object WidgetSnapshotStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(context: Context) = File(context.filesDir, "widget_snapshot.json")

    fun write(context: Context, snapshot: WidgetSnapshot) {
        runCatching { file(context).writeText(json.encodeToString(WidgetSnapshot.serializer(), snapshot)) }
    }

    fun read(context: Context): WidgetSnapshot {
        val f = file(context)
        if (!f.exists()) return WidgetSnapshot()
        return runCatching { json.decodeFromString(WidgetSnapshot.serializer(), f.readText()) }.getOrDefault(WidgetSnapshot())
    }

    /** Costruisce lo snapshot dalle telecamere, ordinate e filtrate secondo le preferenze. */
    fun build(
        loggedIn: Boolean,
        cameras: List<BlinkCamera>,
        prefs: CameraPreferences,
        thumbnailMillis: (BlinkCamera) -> Long?,
    ): WidgetSnapshot {
        // Nel widget mostra le preferite se ce ne sono, altrimenti tutte, nell'ordine scelto.
        val ordered = CameraOrdering.ordered(cameras, prefs)
        val chosen = ordered.filter { prefs.isFavorite(it.key) }.ifEmpty { ordered }
        return WidgetSnapshot(
            loggedIn = loggedIn,
            cameras = chosen.map {
                WidgetCamera(
                    key = it.key,
                    name = it.name,
                    online = it.isOnline,
                    lowBattery = BatteryStatus.isLow(it, prefs),
                    thumbnailMillis = thumbnailMillis(it),
                )
            },
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}
