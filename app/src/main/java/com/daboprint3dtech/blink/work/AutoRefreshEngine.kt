package com.daboprint3dtech.blink.work

import android.util.Log
import com.daboprint3dtech.blink.data.BlinkRepository
import com.daboprint3dtech.blink.data.SnapshotResult
import com.daboprint3dtech.blink.data.settings.AutoRefreshPlanner
import com.daboprint3dtech.blink.data.settings.AutoRefreshStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Riepilogo dell'ultimo giro di aggiornamento automatico (mostrato nelle impostazioni). */
data class AutoRefreshRun(
    val at: Instant,
    val updated: List<String>,
    val unchanged: List<String>,
    val failed: List<String>,
    val skipped: List<String>,
    val nextRunAt: Instant?,
)

/**
 * Esegue l'aggiornamento automatico delle miniature "dovute".
 * Viene chiamato sia dal worker in background sia dal ciclo attivo quando l'app è aperta:
 * il mutex e il calcolo delle scadenze rendono le chiamate ripetute innocue.
 */
class AutoRefreshEngine(
    private val repository: BlinkRepository,
    private val store: AutoRefreshStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()

    private val _lastRun = MutableStateFlow<AutoRefreshRun?>(null)
    val lastRun: StateFlow<AutoRefreshRun?> = _lastRun.asStateFlow()

    private val _nextRunAt = MutableStateFlow<Instant?>(null)
    val nextRunAt: StateFlow<Instant?> = _nextRunAt.asStateFlow()

    /** Aggiorna le telecamere scadute e restituisce quando ricontrollare (null = niente da fare). */
    suspend fun runDue(): Instant? = mutex.withLock {
        val settings = store.current()
        if (!settings.enabled) {
            _nextRunAt.value = null
            return@withLock null
        }
        // Stato recente (max 1 minuto) per conoscere orario delle miniature e stato armato.
        val home = repository.refresh(maxAge = Duration.ofMinutes(1))
        val attempts = store.currentAttempts()
        val startedAt = now()
        val plan = AutoRefreshPlanner.plan(home, settings, attempts, startedAt, zone())

        var results: Map<String, SnapshotResult> = emptyMap()
        if (plan.due.isNotEmpty() || plan.skipped.isNotEmpty()) {
            store.recordAttempts(plan.due.map { it.key } + plan.skipped.keys.map { it.key }, startedAt)
        }
        if (plan.due.isNotEmpty()) {
            Log.i(TAG, "Aggiornamento miniature: ${plan.due.joinToString { it.name }}")
            results = repository.snapshot(plan.due)
        }

        // Ricalcola la prossima scadenza con i dati aggiornati.
        val after = repository.homescreen.value ?: home
        val next = AutoRefreshPlanner.plan(after, settings, store.currentAttempts(), now(), zone()).nextRunAt
            ?.coerceAtLeast(now().plusSeconds(MIN_GAP_SECONDS))

        if (plan.due.isNotEmpty() || plan.skipped.isNotEmpty()) {
            fun names(filter: (SnapshotResult) -> Boolean) = plan.due.filter { cam ->
                results[cam.key]?.let(filter) == true
            }.map { it.name }
            _lastRun.value = AutoRefreshRun(
                at = startedAt,
                updated = names { it is SnapshotResult.Updated },
                unchanged = names { it is SnapshotResult.NotChanged },
                failed = names { it is SnapshotResult.Failed },
                skipped = plan.skipped.map { (cam, reason) -> "${cam.name} (${reason.label})" },
                nextRunAt = next,
            )
        }
        _nextRunAt.value = next
        next
    }

    private companion object {
        const val TAG = "AutoRefresh"
        const val MIN_GAP_SECONDS = 30L
    }
}
