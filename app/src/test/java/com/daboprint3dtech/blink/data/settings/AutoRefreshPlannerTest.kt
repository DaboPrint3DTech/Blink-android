package com.daboprint3dtech.blink.data.settings

import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.BlinkNetwork
import com.daboprint3dtech.blink.data.api.DeviceKind
import com.daboprint3dtech.blink.data.api.Homescreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AutoRefreshPlannerTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = Instant.parse("2026-11-08T10:00:00Z") // 11:00 ora di Roma

    private fun camera(id: Long, thumbAgoMin: Long?, online: Boolean = true, battery: String? = "ok", network: Long = 1) =
        BlinkCamera(
            id = id, networkId = network, name = "Cam$id", kind = DeviceKind.CAMERA, productType = "catalina",
            motionEnabled = true, status = if (online) "done" else "offline", batteryState = battery,
            batteryLevel = null, wifiSignal = null, syncSignal = null, temperatureF = null,
            thumbnailPath = thumbAgoMin?.let { (now.epochSecond - it * 60).toString() },
            serial = null, firmware = null,
        )

    private fun home(vararg cams: BlinkCamera, armed: Boolean = false) =
        Homescreen(listOf(BlinkNetwork(1, "Casa", armed, "online")), cams.toList())

    private val enabled = AutoRefreshSettings(enabled = true, defaultIntervalMinutes = 30)

    @Test
    fun `disattivato non pianifica nulla`() {
        val plan = AutoRefreshPlanner.plan(home(camera(1, 60)), AutoRefreshSettings(), emptyMap(), now, zone)
        assertTrue(plan.due.isEmpty())
        assertNull(plan.nextRunAt)
    }

    @Test
    fun `aggiorna solo le telecamere scadute`() {
        val plan = AutoRefreshPlanner.plan(home(camera(1, 45), camera(2, 10)), enabled, emptyMap(), now, zone)
        assertEquals(listOf(1L), plan.due.map { it.id })
        // Cam2 scade tra 20 minuti, Cam1 tra 30 (dopo lo scatto): il prossimo controllo è tra 20.
        assertEquals(now.plus(Duration.ofMinutes(20)), plan.nextRunAt)
    }

    @Test
    fun `intervallo per telecamera`() {
        val s = enabled.withCamera("camera-2") { it.copy(intervalMinutes = 5) }
        val plan = AutoRefreshPlanner.plan(home(camera(1, 10), camera(2, 6)), s, emptyMap(), now, zone)
        assertEquals(listOf(2L), plan.due.map { it.id })
        assertEquals(now.plus(Duration.ofMinutes(5)), plan.nextRunAt)
    }

    @Test
    fun `telecamera esclusa`() {
        val s = enabled.withCamera("camera-1") { it.copy(included = false) }
        val plan = AutoRefreshPlanner.plan(home(camera(1, 60)), s, emptyMap(), now, zone)
        assertTrue(plan.due.isEmpty())
        assertNull(plan.nextRunAt)
        val s2 = enabled.copy(includeNewCameras = false)
        assertTrue(AutoRefreshPlanner.plan(home(camera(1, 60)), s2, emptyMap(), now, zone).due.isEmpty())
    }

    @Test
    fun `offline e batteria scarica vengono saltate`() {
        val plan = AutoRefreshPlanner.plan(
            home(camera(1, 60, online = false), camera(2, 60, battery = "low"), camera(3, 60)),
            enabled, emptyMap(), now, zone,
        )
        assertEquals(listOf(3L), plan.due.map { it.id })
        assertEquals(setOf(SkipReason.OFFLINE, SkipReason.LOW_BATTERY), plan.skipped.values.toSet())
        val noSkip = AutoRefreshPlanner.plan(home(camera(2, 60, battery = "low")), enabled.copy(skipLowBattery = false), emptyMap(), now, zone)
        assertEquals(listOf(2L), noSkip.due.map { it.id })
    }

    @Test
    fun `un tentativo recente rimanda il prossimo`() {
        val attempts = mapOf("camera-1" to now.minus(Duration.ofMinutes(10)))
        val plan = AutoRefreshPlanner.plan(home(camera(1, 120, online = false)), enabled, attempts, now, zone)
        assertTrue(plan.due.isEmpty() && plan.skipped.isEmpty())
        assertEquals(now.plus(Duration.ofMinutes(20)), plan.nextRunAt)
    }

    @Test
    fun `miniatura sconosciuta va aggiornata subito`() {
        val plan = AutoRefreshPlanner.plan(home(camera(1, null)), enabled, emptyMap(), now, zone)
        assertEquals(1, plan.due.size)
    }

    @Test
    fun `fascia oraria`() {
        val s = enabled.copy(windowEnabled = true, windowStartMinute = 13 * 60, windowEndMinute = 18 * 60)
        val plan = AutoRefreshPlanner.plan(home(camera(1, 60)), s, emptyMap(), now, zone)
        assertTrue(plan.due.isEmpty())
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), plan.nextRunAt) // 13:00 a Roma

        val night = enabled.copy(windowEnabled = true, windowStartMinute = 22 * 60, windowEndMinute = 6 * 60)
        assertTrue(AutoRefreshPlanner.isInWindow(night, LocalTime.of(23, 30)))
        assertTrue(AutoRefreshPlanner.isInWindow(night, LocalTime.of(5, 59)))
        assertFalse(AutoRefreshPlanner.isInWindow(night, LocalTime.of(6, 0)))
        assertEquals(Instant.parse("2026-11-08T21:00:00Z"), AutoRefreshPlanner.nextWindowInstant(night, now, zone))
    }

    @Test
    fun `prossimo controllo spostato all inizio della fascia`() {
        // In fascia fino alle 11:10; la telecamera scade alle 11:20 → si riparte domani alle 08:00.
        val s = enabled.copy(windowEnabled = true, windowStartMinute = 8 * 60, windowEndMinute = 11 * 60 + 10)
        val plan = AutoRefreshPlanner.plan(home(camera(1, 10)), s, emptyMap(), now, zone)
        assertEquals(Instant.parse("2026-11-09T07:00:00Z"), plan.nextRunAt)
    }

    @Test
    fun `filtro sistema armato`() {
        val onlyArmed = enabled.copy(armFilter = ArmFilter.ONLY_ARMED)
        val disarmed = AutoRefreshPlanner.plan(home(camera(1, 60), armed = false), onlyArmed, emptyMap(), now, zone)
        assertTrue(disarmed.due.isEmpty())
        assertEquals(now.plus(AutoRefreshPlanner.ARM_RECHECK), disarmed.nextRunAt)
        val armed = AutoRefreshPlanner.plan(home(camera(1, 60), armed = true), onlyArmed, emptyMap(), now, zone)
        assertEquals(1, armed.due.size)
    }

    @Test
    fun `formattazione intervalli`() {
        assertEquals("5 min", AutoRefreshSettings.formatInterval(5))
        assertEquals("1 ora", AutoRefreshSettings.formatInterval(60))
        assertEquals("3 ore", AutoRefreshSettings.formatInterval(180))
        assertEquals("1 h 30 min", AutoRefreshSettings.formatInterval(90))
        assertEquals("1 giorno", AutoRefreshSettings.formatInterval(1440))
        assertEquals(1, enabled.copy(defaultIntervalMinutes = 0).intervalFor("x"))
    }
}
