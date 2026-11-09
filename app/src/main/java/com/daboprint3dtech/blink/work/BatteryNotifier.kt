package com.daboprint3dtech.blink.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daboprint3dtech.blink.MainActivity
import com.daboprint3dtech.blink.R
import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.prefs.BatteryStatus
import com.daboprint3dtech.blink.data.prefs.CameraPreferencesStore

/**
 * Mostra una notifica quando una o più telecamere scendono sotto la soglia di batteria.
 * Avvisa solo per le telecamere diventate scariche da poco (non ripete l'avviso a ogni giro)
 * e dimentica quelle tornate cariche, così un nuovo calo le fa riavvisare.
 */
class BatteryNotifier(
    private val context: Context,
    private val store: CameraPreferencesStore,
) {
    suspend fun check(cameras: List<BlinkCamera>) {
        val prefs = store.current()
        val alreadyAlerted = store.alertedKeys()
        if (!prefs.batteryAlertEnabled) {
            if (alreadyAlerted.isNotEmpty()) store.setAlertedKeys(emptySet())
            return
        }
        val lowNow = BatteryStatus.lowCameras(cameras, prefs)
        val lowKeys = lowNow.map { it.key }.toSet()
        // Mantieni solo gli avvisi ancora validi e aggiungi i nuovi.
        val known = cameras.map { it.key }.toSet()
        val stillAlerted = alreadyAlerted.intersect(lowKeys)
        val newlyLow = lowNow.filter { it.key !in alreadyAlerted }
        // Rimuovi dagli "avvisati" le telecamere non più scariche o sparite.
        store.setAlertedKeys(stillAlerted + newlyLow.map { it.key })
        if (newlyLow.isEmpty()) return
        notify(newlyLow, lowNow)
    }

    private fun notify(newlyLow: List<BlinkCamera>, allLow: List<BlinkCamera>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val names = newlyLow.joinToString { it.name }
        val title = if (newlyLow.size == 1) "Batteria scarica: ${newlyLow.first().name}" else "Batteria scarica (${newlyLow.size})"
        val text = if (newlyLow.size == 1) "La telecamera ${names} ha la batteria quasi scarica." else "Batteria quasi scarica: $names"
        val intent = Intent(context, MainActivity::class.java)
            .setAction("open-battery")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Batteria scarica", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avvisi quando una telecamera ha la batteria quasi scarica"
                },
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "battery_low"
        const val NOTIFICATION_ID = 4201
    }
}
