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
    /** Comando inviato ma la nuova miniatura non è ancora comparsa: verrà scaricata al controllo successivo. */
    val unchanged: List<String>,
    /** "Nome (motivo)". */
    val failed: List<String>,
    /** "Nome (motivo)". */
    val skipped: List<String>,
    val nextRunAt: Instant?,
    /** Telecamere incluse ma non ancora scadute, con la prossima scadenza. */
    val notDue: List<Pair<String, Instant>> = emptyList(),
)

/**
 * Esegue l'aggiornamento automatico delle miniature "dovute".
 * Viene chiamato sia dal worker in background sia dal ciclo attivo quando l'app è aperta:
 * il mutex e il calcolo delle scadenze rendono le chiamate ripetute innocue.
 *
 * Il tentativo viene registrato per ogni telecamera solo quando il suo comando sta per partire:
 * se il giro viene interrotto (app chiusa, impostazioni modificate, Android che ferma il lavoro)
 * le telecamere non ancora raggiunte restano da aggiornare e vengono riprese al giro successivo.
 */
class AutoRefreshEngine(
    private val repository: BlinkRepository,
    private val store: AutoRefreshStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
    /** Eseguito al termine di ogni giro (es. notifica batteria, aggiornamento widget). */
    private val afterRun: suspend () -> Unit = {},
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
        val startedAt = now()
        val plan = AutoRefreshPlanner.plan(home, settings, store.currentAttempts(), startedAt, zone())

        // Le telecamere saltate (offline / batteria scarica) si ritentano dopo un intervallo.
        store.recordAttempts(plan.skipped.keys.map { it.key }, startedAt)

        var results: Map<String, SnapshotResult> = emptyMap()
        if (plan.due.isNotEmpty()) {
            Log.i(TAG, "Aggiornamento miniature: ${plan.due.joinToString { it.name }}")
            results = repository.snapshot(plan.due) { camera ->
                store.recordAttempts(listOf(camera.key), now())
            }
        }

        // Ricalcola la prossima scadenza con i dati aggiornati.
        val after = repository.homescreen.value ?: home
        val afterPlan = AutoRefreshPlanner.plan(after, settings, store.currentAttempts(), now(), zone())
        var next = afterPlan.nextRunAt
        // Miniature non ancora arrivate: ricontrollo a breve solo per scaricarle (nessun nuovo scatto).
        if (results.values.any { it is SnapshotResult.NotChanged }) {
            val followUp = now().plus(FOLLOW_UP)
            next = next?.let { minOf(it, followUp) } ?: followUp
        }
        next = next?.coerceAtLeast(now().plusSeconds(MIN_GAP_SECONDS))

        if (plan.due.isNotEmpty() || plan.skipped.isNotEmpty()) {
            fun names(filter: (SnapshotResult) -> Boolean) =
                plan.due.filter { cam -> results[cam.key]?.let(filter) == true }.map { it.name }
            _lastRun.value = AutoRefreshRun(
                at = startedAt,
                updated = names { it is SnapshotResult.Updated },
                unchanged = names { it is SnapshotResult.NotChanged },
                failed = plan.due.mapNotNull { cam ->
                    (results[cam.key] as? SnapshotResult.Failed)?.let { f ->
                        "${cam.name} (${f.error.message ?: f.error.javaClass.simpleName})"
                    }
                },
                skipped = plan.skipped.map { (cam, reason) -> "${cam.name} (${reason.label})" },
                nextRunAt = next,
                notDue = plan.notDue.map { (cam, at) -> cam.name to at },
            )
        }
        _nextRunAt.value = next
        runCatching { afterRun() }
        next
    }

    private companion object {
        const val TAG = "AutoRefresh"
        const val MIN_GAP_SECONDS = 30L
        val FOLLOW_UP: Duration = Duration.ofMinutes(2)
    }
}
