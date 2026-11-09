package com.daboprint3dtech.blink.data.settings

import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.Homescreen
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SkipReason(val label: String) {
    OFFLINE("telecamera offline"),
    LOW_BATTERY("batteria scarica"),
}

/** Risultato della pianificazione: chi aggiornare ora e quando ricontrollare. */
data class RefreshPlan(
    val due: List<BlinkCamera>,
    val skipped: Map<BlinkCamera, SkipReason>,
    /** Prossimo controllo necessario, null se l'aggiornamento automatico non ha nulla da fare. */
    val nextRunAt: Instant?,
    /** Telecamere incluse ma non ancora scadute, con la loro prossima scadenza. */
    val notDue: Map<BlinkCamera, Instant> = emptyMap(),
)

/**
 * Logica pura (testabile) che decide quali miniature aggiornare.
 *
 * Una telecamera è "da aggiornare" quando è passato il suo intervallo dall'ultima miniatura
 * disponibile — quella scattata automaticamente, a mano o da un evento di movimento —
 * oppure dall'ultimo tentativo (così una telecamera offline non viene ritentata di continuo).
 */
object AutoRefreshPlanner {

    /** Tolleranza: se manca meno di questo tempo si aggiorna subito invece di rimandare. */
    val DUE_TOLERANCE: Duration = Duration.ofSeconds(30)

    /** Ricontrollo quando nessuna telecamera è idonea solo per via dello stato armato/disarmato. */
    val ARM_RECHECK: Duration = Duration.ofMinutes(15)

    /** Oltre questo margine un orario "nel futuro" viene ignorato (orologi sfasati o formato inatteso). */
    val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

    fun lastRefresh(camera: BlinkCamera, attempts: Map<String, Instant>, now: Instant = Instant.now()): Instant? {
        val limit = now.plus(CLOCK_SKEW)
        return listOfNotNull(camera.thumbnailTime, attempts[camera.key])
            .filter { !it.isAfter(limit) }
            .maxOrNull()
    }

    fun nextDueAt(
        camera: BlinkCamera,
        settings: AutoRefreshSettings,
        attempts: Map<String, Instant>,
        now: Instant = Instant.now(),
    ): Instant {
        val last = lastRefresh(camera, attempts, now) ?: return Instant.EPOCH
        return last.plus(Duration.ofMinutes(settings.intervalFor(camera.key).toLong()))
    }

    fun isInWindow(settings: AutoRefreshSettings, time: LocalTime): Boolean {
        if (!settings.windowEnabled) return true
        val minute = time.hour * 60 + time.minute
        val start = settings.windowStartMinute
        val end = settings.windowEndMinute
        return when {
            start == end -> true
            start < end -> minute in start until end
            else -> minute >= start || minute < end // fascia a cavallo della mezzanotte
        }
    }

    /** Primo istante >= [from] che cade nella fascia oraria. */
    fun nextWindowInstant(settings: AutoRefreshSettings, from: Instant, zone: ZoneId): Instant {
        val zoned = from.atZone(zone)
        if (isInWindow(settings, zoned.toLocalTime())) return from
        val startTime = LocalTime.of(settings.windowStartMinute / 60, settings.windowStartMinute % 60)
        var candidate: ZonedDateTime = zoned.toLocalDate().atTime(startTime).atZone(zone)
        if (!candidate.toInstant().isAfter(from)) candidate = candidate.plusDays(1)
        return candidate.toInstant()
    }

    fun isEligibleByArmState(settings: AutoRefreshSettings, home: Homescreen, camera: BlinkCamera): Boolean {
        val armed = home.network(camera.networkId)?.armed ?: return true
        return when (settings.armFilter) {
            ArmFilter.ALWAYS -> true
            ArmFilter.ONLY_ARMED -> armed
            ArmFilter.ONLY_DISARMED -> !armed
        }
    }

    fun plan(
        home: Homescreen,
        settings: AutoRefreshSettings,
        attempts: Map<String, Instant>,
        now: Instant,
        zone: ZoneId,
    ): RefreshPlan {
        if (!settings.enabled) return RefreshPlan(emptyList(), emptyMap(), null)
        val included = home.cameras.filter { settings.isIncluded(it.key) }
        if (included.isEmpty()) return RefreshPlan(emptyList(), emptyMap(), null)

        val eligible = included.filter { isEligibleByArmState(settings, home, it) }
        if (eligible.isEmpty()) return RefreshPlan(emptyList(), emptyMap(), now.plus(ARM_RECHECK))

        if (!isInWindow(settings, now.atZone(zone).toLocalTime())) {
            return RefreshPlan(emptyList(), emptyMap(), nextWindowInstant(settings, now, zone))
        }

        val due = mutableListOf<BlinkCamera>()
        val skipped = linkedMapOf<BlinkCamera, SkipReason>()
        val notDue = linkedMapOf<BlinkCamera, Instant>()
        val nextTimes = mutableListOf<Instant>()
        val threshold = now.plus(DUE_TOLERANCE)
        for (camera in eligible) {
            val dueAt = nextDueAt(camera, settings, attempts, now)
            if (!dueAt.isAfter(threshold)) {
                when {
                    !camera.isOnline -> skipped[camera] = SkipReason.OFFLINE
                    settings.skipLowBattery && camera.isLowBattery -> skipped[camera] = SkipReason.LOW_BATTERY
                    else -> due += camera
                }
                // Dopo questo giro il prossimo turno della telecamera è tra un intervallo.
                nextTimes += now.plus(Duration.ofMinutes(settings.intervalFor(camera.key).toLong()))
            } else {
                notDue[camera] = dueAt
                nextTimes += dueAt
            }
        }
        var next = nextTimes.minOrNull()
        if (next != null && included.size != eligible.size) {
            next = minOf(next, now.plus(ARM_RECHECK))
        }
        next = next?.let { nextWindowInstant(settings, it, zone) }
        return RefreshPlan(due, skipped, next, notDue)
    }
}
