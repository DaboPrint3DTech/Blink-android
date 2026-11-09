package com.daboprint3dtech.blink.work

import android.util.Log
import com.daboprint3dtech.blink.data.auth.AuthManager
import com.daboprint3dtech.blink.data.auth.AuthState
import com.daboprint3dtech.blink.data.settings.AutoRefreshSettings
import com.daboprint3dtech.blink.data.settings.AutoRefreshStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.Instant

/**
 * Mentre l'app è in primo piano esegue l'aggiornamento automatico con precisione al secondo
 * (WorkManager in background può essere ritardato da Android). Si riavvia a ogni modifica
 * delle impostazioni o del login.
 *
 * Quando l'app va in background il ciclo viene interrotto: in quel momento si riattiva subito
 * il lavoro in background, che riprende le telecamere eventualmente rimaste da aggiornare.
 */
class ForegroundAutoRefresh(
    private val auth: AuthManager,
    private val store: AutoRefreshStore,
    private val engine: AutoRefreshEngine,
    private val scheduler: RefreshScheduler,
) {
    suspend fun run() {
        var active: AutoRefreshSettings? = null
        try {
            loop { active = it }
        } finally {
            active?.let { scheduler.restart(it.unmeteredOnly) }
        }
    }

    private suspend fun loop(onActive: (AutoRefreshSettings?) -> Unit) {
        combine(auth.state, store.settings) { state, settings -> (state is AuthState.LoggedIn) to settings }
            .distinctUntilChanged()
            .collectLatest { (loggedIn, settings) ->
                if (!loggedIn || !settings.enabled) {
                    onActive(null)
                    return@collectLatest
                }
                onActive(settings)
                while (true) {
                    val next = try {
                        engine.runDue()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ForegroundRefresh", "Aggiornamento non riuscito", e)
                        Instant.now().plus(RETRY)
                    }
                    val wait = next?.let { Duration.between(Instant.now(), it) } ?: MAX_WAIT
                    delay(wait.coerceIn(MIN_WAIT, MAX_WAIT).toMillis())
                }
            }
    }

    private companion object {
        val MIN_WAIT: Duration = Duration.ofSeconds(15)
        val MAX_WAIT: Duration = Duration.ofMinutes(10)
        val RETRY: Duration = Duration.ofMinutes(2)
    }
}
