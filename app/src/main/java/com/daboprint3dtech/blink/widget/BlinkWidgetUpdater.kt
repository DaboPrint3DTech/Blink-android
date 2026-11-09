package com.daboprint3dtech.blink.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Scrive lo snapshot su disco e chiede a Glance di ridisegnare il widget.
 * Lo snapshot viene prodotto da chi ha lo stato in memoria (il contenitore dell'app).
 */
class BlinkWidgetUpdater(private val context: Context) {
    @Volatile
    var snapshotProvider: (suspend () -> WidgetSnapshot)? = null

    suspend fun update() {
        val snapshot = snapshotProvider?.invoke() ?: return
        WidgetSnapshotStore.write(context, snapshot)
        runCatching { BlinkWidget().updateAll(context) }
    }
}
