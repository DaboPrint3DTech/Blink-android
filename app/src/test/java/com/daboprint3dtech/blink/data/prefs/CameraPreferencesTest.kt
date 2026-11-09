package com.daboprint3dtech.blink.data.prefs

import com.daboprint3dtech.blink.data.api.BlinkCamera
import com.daboprint3dtech.blink.data.api.DeviceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPreferencesTest {
    private fun cam(id: Long, name: String = "Cam$id", battery: String? = "ok", level: Int? = 3) =
        BlinkCamera(
            id = id, networkId = 1, name = name, kind = DeviceKind.CAMERA, productType = "catalina",
            motionEnabled = true, status = "done", batteryState = battery, batteryLevel = level,
            wifiSignal = 4, syncSignal = 5, temperatureF = 68, thumbnailPath = "1700000000",
            serial = null, firmware = null,
        )

    private val cams = listOf(cam(1), cam(2), cam(3), cam(4))

    @Test
    fun `ordine personalizzato con telecamere nuove in fondo`() {
        val prefs = CameraPreferences(order = listOf("camera-3", "camera-1"))
        assertEquals(
            listOf("camera-3", "camera-1", "camera-2", "camera-4"),
            CameraOrdering.orderedKeys(cams, prefs.copy(favoritesFirst = false)),
        )
    }

    @Test
    fun `preferite in testa mantenendo l ordine`() {
        val prefs = CameraPreferences(order = listOf("camera-4", "camera-3", "camera-2", "camera-1"), favorites = setOf("camera-2"))
        assertEquals(
            listOf("camera-2", "camera-4", "camera-3", "camera-1"),
            CameraOrdering.orderedKeys(cams, prefs),
        )
    }

    @Test
    fun `sposta su e giu`() {
        val order = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), CameraOrdering.move(order, "b", up = true))
        assertEquals(listOf("a", "c", "b"), CameraOrdering.move(order, "c", up = true))
        assertEquals(listOf("a", "c", "b"), CameraOrdering.move(order, "b", up = false))
        // ai bordi non cambia nulla
        assertEquals(order, CameraOrdering.move(order, "a", up = true))
        assertEquals(order, CameraOrdering.move(order, "c", up = false))
        assertEquals(order, CameraOrdering.move(order, "x", up = true))
    }

    @Test
    fun `toggle preferito`() {
        val p = CameraPreferences().toggleFavorite("camera-1")
        assertTrue(p.isFavorite("camera-1"))
        assertFalse(p.toggleFavorite("camera-1").isFavorite("camera-1"))
    }

    @Test
    fun `stato batteria e soglia`() {
        val prefs = CameraPreferences(batteryAlertLevel = 1)
        assertFalse(BatteryStatus.isLow(cam(1, level = 3), prefs))
        assertTrue(BatteryStatus.isLow(cam(1, level = 1), prefs))
        assertTrue(BatteryStatus.isLow(cam(1, battery = "low", level = 2), prefs)) // stato "low" sempre
        // soglia più alta
        assertTrue(BatteryStatus.isLow(cam(1, level = 2), prefs.copy(batteryAlertLevel = 2)))
        // telecamera alimentata: niente batteria, mai "low"
        val powered = cam(1, battery = null, level = null)
        assertFalse(BatteryStatus.hasBattery(powered))
        assertFalse(BatteryStatus.isLow(powered, prefs))
    }

    @Test
    fun `elenco telecamere scariche`() {
        val list = listOf(cam(1, level = 3), cam(2, level = 1), cam(3, battery = "low", level = 2), cam(4, battery = null, level = null))
        val low = BatteryStatus.lowCameras(list, CameraPreferences(batteryAlertLevel = 1))
        assertEquals(listOf("camera-2", "camera-3"), low.map { it.key })
    }
}
