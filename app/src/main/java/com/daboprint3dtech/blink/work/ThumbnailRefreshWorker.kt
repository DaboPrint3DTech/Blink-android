package com.daboprint3dtech.blink.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.daboprint3dtech.blink.BlinkApp
import com.daboprint3dtech.blink.data.api.BlinkAuthException
import com.daboprint3dtech.blink.data.auth.AuthState
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

/** Lavoro in background che aggiorna le miniature scadute e si ripianifica. */
class ThumbnailRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BlinkApp).container
        container.auth.init()
        if (container.auth.state.value !is AuthState.LoggedIn) return Result.success()

        val settings = container.autoRefreshStore.current()
        if (!settings.enabled) return Result.success()

        val next: Instant? = try {
            container.autoRefreshEngine.runDue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlinkAuthException) {
            Log.w(TAG, "Sessione non valida, aggiornamento automatico sospeso", e)
            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Aggiornamento automatico non riuscito, nuovo tentativo più tardi", e)
            Instant.now().plus(Duration.ofMinutes(settings.defaultIntervalMinutes.coerceIn(1, 15).toLong()))
        }
        if (next != null) container.refreshScheduler.scheduleNext(next, settings.unmeteredOnly)
        return Result.success()
    }

    private companion object {
        const val TAG = "ThumbnailWorker"
    }
}
