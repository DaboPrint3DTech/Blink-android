package com.daboprint3dtech.blink.work

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daboprint3dtech.blink.data.BlinkRepository
import com.daboprint3dtech.blink.data.api.BlinkApi
import com.daboprint3dtech.blink.data.api.OAuthClient
import com.daboprint3dtech.blink.data.auth.AuthManager
import com.daboprint3dtech.blink.data.auth.Session
import com.daboprint3dtech.blink.data.settings.AutoRefreshStore
import com.daboprint3dtech.blink.data.settings.CameraRefreshConfig
import com.daboprint3dtech.blink.data.thumbs.ThumbnailCache
import com.daboprint3dtech.blink.testutil.tempSessionStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Verifica il giro completo: pianificazione → scatto → nuova miniatura scaricata in cache. */
class AutoRefreshEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: AutoRefreshEngine
    private lateinit var store: AutoRefreshStore
    private lateinit var cache: ThumbnailCache
    private val snapshots = AtomicInteger()
    private val thumbTs = AtomicLong(Instant.now().minus(Duration.ofHours(2)).epochSecond)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun homescreen() = """
        {"networks":[{"id":2001,"name":"Casa","armed":false}],
         "cameras":[
           {"id":4001,"name":"Ingresso","type":"catalina","enabled":true,"status":"done","battery":"ok",
            "network_id":2001,"thumbnail":"${thumbTs.get()}"},
           {"id":4002,"name":"Retro","type":"catalina","enabled":true,"status":"done","battery":"ok",
            "network_id":2001,"thumbnail":"${thumbTs.get()}"}
         ]}
    """.trimIndent()

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/homescreen") -> MockResponse().setBody(homescreen())
                    path == "/network/2001/camera/4001/thumbnail" -> {
                        snapshots.incrementAndGet()
                        thumbTs.set(Instant.now().epochSecond) // la telecamera produce una nuova miniatura
                        MockResponse().setBody("""{"id":7,"network_id":2001}""")
                    }
                    path == "/network/2001/command/7" -> MockResponse().setBody("""{"complete":true}""")
                    path.endsWith("/thumbnail/thumbnail.jpg") -> MockResponse().setBody(Buffer().write(jpeg))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val sessions = tempSessionStore()
        sessions.save(Session("me@example.com", "AT", "RT", System.currentTimeMillis() + 3_600_000, "e006", 4242))
        val auth = AuthManager(OAuthClient(OkHttpClient(), oauthBaseUrl = base), sessions)
        val api = BlinkApi(OkHttpClient(), auth, baseUrlFor = { base }, commandPollIntervalMs = 5, busyRetryDelayMs = 5)
        cache = ThumbnailCache(Files.createTempDirectory("thumbs").toFile())
        val repo = BlinkRepository(api, cache, thumbnailSettleDelayMs = 5)
        val dir = Files.createTempDirectory("ar").toFile()
        store = AutoRefreshStore(PreferenceDataStoreFactory.create(produceFile = { File(dir, "ar.preferences_pb") }))
        store.update {
            it.copy(
                enabled = true,
                defaultIntervalMinutes = 30,
                includeNewCameras = false,
                cameras = mapOf("camera-4001" to CameraRefreshConfig(included = true, intervalMinutes = 15)),
            )
        }
        engine = AutoRefreshEngine(repo, store, zone = { ZoneId.of("Europe/Rome") })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `aggiorna la miniatura scaduta e pianifica il prossimo giro`() = runTest {
        val start = Instant.now()
        val next = engine.runDue()

        assertEquals(1, snapshots.get())
        assertNotNull(next)
        val delay = Duration.between(start, next)
        assertTrue("prossimo giro tra ~15 min, era $delay", delay > Duration.ofMinutes(14) && delay <= Duration.ofMinutes(16))

        // Nuova miniatura scaricata e salvata, con l'URL che contiene il nuovo timestamp.
        assertArrayEquals(jpeg, cache.file("camera-4001").readBytes())
        assertTrue(cache.cachedUrl("camera-4001")!!.contains("ts=${thumbTs.get()}"))
        assertTrue(store.currentAttempts().containsKey("camera-4001"))
        assertEquals(listOf("Ingresso"), engine.lastRun.value!!.updated)

        // Subito dopo non c'è nulla da aggiornare: nessun nuovo scatto.
        engine.runDue()
        assertEquals(1, snapshots.get())
    }

    @Test
    fun `disattivato non fa nulla`() = runTest {
        store.update { it.copy(enabled = false) }
        assertEquals(null, engine.runDue())
        assertEquals(0, snapshots.get())
    }
}
