package com.daboprint3dtech.blink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Pianifica il [ThumbnailRefreshWorker] con WorkManager.
 *
 * Invece di un PeriodicWorkRequest (minimo 15 minuti) si usa una catena di lavori one-shot:
 * ogni esecuzione calcola la prossima scadenza e accoda il lavoro successivo con il ritardo
 * esatto. Così sono possibili anche intervalli brevi (es. 5 minuti); quando il telefono è in
 * modalità Doze Android può comunque raggruppare/ritardare le esecuzioni.
 */
class RefreshScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    /** Riprogramma da zero (es. dopo una modifica delle impostazioni): esegue subito un controllo. */
    fun restart(unmeteredOnly: Boolean) = enqueue(Instant.now(), unmeteredOnly, ExistingWorkPolicy.REPLACE)

    /** Avvia la catena solo se non è già pianificata (es. all'avvio dell'app). */
    fun ensureScheduled(unmeteredOnly: Boolean) = enqueue(Instant.now(), unmeteredOnly, ExistingWorkPolicy.KEEP)

    /** Chiamato dal worker al termine: accoda l'esecuzione successiva dopo quella corrente. */
    fun scheduleNext(at: Instant, unmeteredOnly: Boolean) =
        enqueue(at, unmeteredOnly, ExistingWorkPolicy.APPEND_OR_REPLACE)

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(at: Instant, unmeteredOnly: Boolean, policy: ExistingWorkPolicy) {
        val delayMs = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ThumbnailRefreshWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "thumbnail-auto-refresh"
    }
}
