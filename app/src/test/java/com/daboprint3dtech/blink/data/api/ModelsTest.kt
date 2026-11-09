package com.daboprint3dtech.blink.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModelsTest {
    private val home = Homescreen.parse(parseJsonObject(HOMESCREEN_JSON)!!)
    private val base = "https://rest-e006.immedia-semi.com"

    @Test
    fun `parsing della homescreen`() {
        assertEquals(listOf("Casa", "Garage"), home.networks.map { it.name })
        assertTrue(home.network(2001)!!.armed)
        assertEquals("online", home.network(2001)!!.syncModuleStatus)
        assertNull(home.network(2002)!!.syncModuleStatus)

        assertEquals(4, home.cameras.size)
        val ingresso = home.camera("camera-4001")!!
        assertEquals(DeviceKind.CAMERA, ingresso.kind)
        assertTrue(ingresso.motionEnabled)
        assertTrue(ingresso.isOnline)
        assertFalse(ingresso.isLowBattery)
        assertEquals(4, ingresso.wifiSignal)
        assertEquals(20.0, ingresso.temperatureC!!, 0.01)

        val giardino = home.camera("camera-4002")!!
        assertFalse(giardino.isOnline)
        assertTrue(giardino.isLowBattery)
        assertFalse(giardino.motionEnabled)

        assertEquals(DeviceKind.MINI, home.camera("mini-5001")!!.kind)
        assertEquals(DeviceKind.DOORBELL, home.camera("doorbell-6001")!!.kind)
        assertEquals(listOf("camera-4001", "camera-4002"), home.camerasOf(2001).map { it.key })
    }

    @Test
    fun `url delle miniature`() {
        // API vecchia: si aggiunge .jpg
        assertEquals(
            "$base/media/production/account/4242/network/2001/camera/4001/clip_HIwo6g_2020_10_02__02_28PM.jpg",
            BlinkUrls.thumbnailUrl(base, 4242, home.camera("camera-4001")!!),
        )
        // URL v3 completo: invariato
        assertEquals(
            "$base/api/v3/media/accounts/4242/networks/2001/sedona/4002/thumbnail/thumbnail.jpg?ts=1700000000&ext=",
            BlinkUrls.thumbnailUrl(base, 4242, home.camera("camera-4002")!!),
        )
        // Solo timestamp: si costruisce l'URL v3 con il tipo prodotto
        assertEquals(
            "$base/api/v3/media/accounts/4242/networks/2002/owl/5001/thumbnail/thumbnail.jpg?ts=1700000600&ext=",
            BlinkUrls.thumbnailUrl(base, 4242, home.camera("mini-5001")!!),
        )
        assertNull(BlinkUrls.thumbnailUrl(base, 4242, home.camera("mini-5001")!!.copy(thumbnailPath = "")))
    }

    @Test
    fun `orario delle miniature`() {
        assertEquals(Instant.parse("2020-10-02T14:28:00Z"), home.camera("camera-4001")!!.thumbnailTime)
        assertEquals(Instant.ofEpochSecond(1700000000), home.camera("camera-4002")!!.thumbnailTime)
        assertEquals(Instant.ofEpochSecond(1700000600), home.camera("mini-5001")!!.thumbnailTime)
        assertEquals(Instant.parse("2020-10-02T00:17:00Z"), BlinkUrls.thumbnailTimestamp("clip_rHLLGqU_2020_10_02__12_17AM"))
        assertNull(BlinkUrls.thumbnailTimestamp(null))
    }

    @Test
    fun `parsing delle clip`() {
        val json = parseJsonObject(
            """{"limit":25,"media":[
              {"id":11,"created_at":"2020-10-02T00:28:38+00:00","deleted":false,"device_id":4001,"device_name":"Ingresso",
               "network_id":2001,"network_name":"Casa","type":"video","source":"pir","watched":false,
               "thumbnail":"/api/v2/accounts/4242/media/thumb/11","media":"/api/v2/accounts/4242/media/clip/11.mp4"},
              {"id":12,"deleted":true,"media":"/x.mp4"},
              {"id":13,"device_name":"Salotto"}
            ]}""",
        )!!
        val clips = Clip.parseList(json)
        assertEquals(listOf(11L), clips.map { it.id })
        val clip = clips.single()
        assertEquals(Instant.parse("2020-10-02T00:28:38Z"), clip.createdAt)
        assertEquals("$base/api/v2/accounts/4242/media/thumb/11.jpg", BlinkUrls.clipThumbnailUrl(base, clip))
        assertEquals("$base/api/v2/accounts/4242/media/clip/11.mp4", BlinkUrls.clipVideoUrl(base, clip))
    }
}
