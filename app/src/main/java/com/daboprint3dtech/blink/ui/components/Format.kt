package com.daboprint3dtech.blink.ui.components

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ITALIAN)
private val fullDateTimeFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm:ss", Locale.ITALIAN)

/** "adesso", "5 min fa", "2 h fa", "3 g fa". */
fun relativeTime(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return "orario sconosciuto"
    val d = Duration.between(instant, now)
    return when {
        d.isNegative || d.seconds < 45 -> "adesso"
        d.toMinutes() < 60 -> "${d.toMinutes().coerceAtLeast(1)} min fa"
        d.toHours() < 24 -> "${d.toHours()} h fa"
        else -> "${d.toDays()} g fa"
    }
}

/** "tra 5 min", "alle 14:30", "domani alle 07:00". */
fun upcomingTime(instant: Instant?, now: Instant = Instant.now()): String {
    if (instant == null) return "—"
    val d = Duration.between(now, instant)
    val zone = ZoneId.systemDefault()
    return when {
        d.seconds <= 45 -> "a breve"
        d.toMinutes() < 60 -> "tra ${d.toMinutes() + 1} min"
        instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() -> "alle ${timeFormat.format(instant.atZone(zone))}"
        instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate().plusDays(1) ->
            "domani alle ${timeFormat.format(instant.atZone(zone))}"
        else -> dateTimeFormat.format(instant.atZone(zone))
    }
}

fun shortDateTime(instant: Instant?): String =
    instant?.let { dateTimeFormat.format(it.atZone(ZoneId.systemDefault())) } ?: "—"

fun fullDateTime(instant: Instant?): String =
    instant?.let { fullDateTimeFormat.format(it.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.systemDefault())) } ?: "—"
