package com.daboprint3dtech.blink.widget

import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.DeviceKind
import com.daboprint3dtech.blink.data.prefs.CameraPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    private fun cam(id: Long, online: Boolean = true, battery: String? = "ok", level: Int? = 3) =
        BlinkCamera(
            id = id, networkId = 1, name = "Cam$id", kind = DeviceKind.CAMERA, productType = "catalina",
            motionEnabled = true, status = if (online) "done" else "offline", batteryState = battery,
            batteryLevel = level, wifiSignal = 4, syncSignal = 5, temperatureF = 68,
            thumbnailPath = "1700000000", serial = null, firmware = null,
        )

    private val cams = listOf(cam(1), cam(2), cam(3))

    @Test
    fun `mostra solo le preferite se presenti, nell ordine scelto`() {
        val prefs = CameraPreferences(order = listOf("camera-3", "camera-1", "camera-2"), favorites = setOf("camera-1", "camera-3"))
        val snap = WidgetSnapshotStore.build(loggedIn = true, cameras = cams, prefs = prefs, thumbnailMillis = { 1000L })
        assertTrue(snap.loggedIn)
        assertEquals(listOf("camera-3", "camera-1"), snap.cameras.map { it.key })
        assertEquals(1000L, snap.cameras.first().thumbnailMillis)
    }

    @Test
    fun `senza preferite mostra tutte nell ordine`() {
        val prefs = CameraPreferences(order = listOf("camera-2", "camera-1", "camera-3"))
        val snap = WidgetSnapshotStore.build(loggedIn = true, cameras = cams, prefs = prefs, thumbnailMillis = { null })
        assertEquals(listOf("camera-2", "camera-1", "camera-3"), snap.cameras.map { it.key })
    }

    @Test
    fun `segnala offline e batteria scarica`() {
        val list = listOf(cam(1, online = false), cam(2, battery = "low", level = 1))
        val prefs = CameraPreferences(batteryAlertLevel = 1)
        val snap = WidgetSnapshotStore.build(loggedIn = true, cameras = list, prefs = prefs, thumbnailMillis = { 1L })
        assertFalse(snap.cameras[0].online)
        assertTrue(snap.cameras[1].lowBattery)
    }
}
