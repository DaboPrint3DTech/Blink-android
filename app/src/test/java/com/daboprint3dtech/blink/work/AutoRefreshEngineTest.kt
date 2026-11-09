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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Giro completo su un sistema con più telecamere: pianificazione → scatto di ciascuna →
 * attesa della nuova miniatura → download in cache.
 */
class AutoRefreshEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var engine: AutoRefreshEngine
    private lateinit var store: AutoRefreshStore
    private lateinit var cache: ThumbnailCache

    private val cameraIds = listOf(4001L, 4002L, 4003L)
    private val oldTs = Instant.now().minus(Duration.ofHours(2)).epochSecond
    private val thumbTs = ConcurrentHashMap(cameraIds.associateWith { oldTs })
    private val snapshots = ConcurrentHashMap<Long, AtomicInteger>()

    /** Dopo il comando la nuova miniatura compare solo dopo questo numero di letture della homescreen. */
    @Volatile private var thumbnailDelayReads = 0
    private val pendingThumb = ConcurrentHashMap<Long, Int>()

    /** Se impostato, il comando di questa telecamera resta "in corso" finché il latch non si apre. */
    @Volatile private var blockCommandOf: Long? = null
    private val firstCommandSent = CountDownLatch(1)
    private val releaseCommand = CountDownLatch(1)

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun homescreen(): String {
        // Le miniature "in arrivo" compaiono dopo qualche lettura.
        for ((id, left) in pendingThumb) {
            if (left <= 0) {
                thumbTs[id] = Instant.now().epochSecond
                pendingThumb.remove(id)
            } else {
                pendingThumb[id] = left - 1
            }
        }
        val cameras = cameraIds.joinToString(",") { id ->
            """{"id":$id,"name":"Cam$id","type":"catalina","enabled":true,"status":"done","battery":"ok",
                "network_id":2001,"thumbnail":"${thumbTs[id]}"}"""
        }
        return """{"networks":[{"id":2001,"name":"Casa","armed":false}],"cameras":[$cameras]}"""
    }

    @Before
    fun setUp() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val snap = Regex("/network/2001/camera/(\\d+)/thumbnail").matchEntire(path)
                return when {
                    path.endsWith("/homescreen") -> MockResponse().setBody(homescreen())
                    snap != null -> {
                        val id = snap.groupValues[1].toLong()
                        snapshots.getOrPut(id) { AtomicInteger() }.incrementAndGet()
                        pendingThumb[id] = thumbnailDelayReads
                        firstCommandSent.countDown()
                        MockResponse().setBody("""{"id":${id + 10},"network_id":2001}""")
                    }
                    path.startsWith("/network/2001/command/") -> {
                        val cameraId = path.substringAfterLast('/').toLong() - 10
                        val blocked = cameraId == blockCommandOf && releaseCommand.count > 0
                        MockResponse().setBody(if (blocked) """{"complete":false,"status_code":908}""" else """{"complete":true}""")
                    }
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
        val repo = BlinkRepository(api, cache, settleDelaysMs = List(8) { 20L })
        val dir = Files.createTempDirectory("ar").toFile()
        store = AutoRefreshStore(PreferenceDataStoreFactory.create(produceFile = { File(dir, "ar.preferences_pb") }))
        store.update {
            it.copy(
                enabled = true,
                defaultIntervalMinutes = 30,
                cameras = mapOf("camera-4001" to CameraRefreshConfig(intervalMinutes = 15)),
            )
        }
        engine = AutoRefreshEngine(repo, store, zone = { ZoneId.of("Europe/Rome") })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun snapCount(id: Long) = snapshots[id]?.get() ?: 0

    @Test
    fun `aggiorna tutte le telecamere scadute e pianifica il prossimo giro`() = runBlocking {
        val start = Instant.now()
        val next = engine.runDue()

        cameraIds.forEach { assertEquals("scatti di $it", 1, snapCount(it)) }
        // Cam4001 ha intervallo 15 min, le altre 30: il prossimo giro è tra ~15 minuti.
        assertNotNull(next)
        val delay = Duration.between(start, next)
        assertTrue("prossimo giro tra ~15 min, era $delay", delay > Duration.ofMinutes(14) && delay <= Duration.ofMinutes(16))

        cameraIds.forEach { id ->
            assertArrayEquals(jpeg, cache.file("camera-$id").readBytes())
            assertTrue(cache.cachedUrl("camera-$id")!!.contains("ts=${thumbTs[id]}"))
        }
        assertEquals(listOf("Cam4001", "Cam4002", "Cam4003"), engine.lastRun.value!!.updated)

        // Subito dopo non c'è nulla da aggiornare: nessun nuovo scatto.
        engine.runDue()
        cameraIds.forEach { assertEquals(1, snapCount(it)) }
        assertEquals(3, engine.lastRun.value!!.updated.size)
    }

    @Test
    fun `un giro interrotto non salta le telecamere non ancora raggiunte`() = runBlocking {
        blockCommandOf = 4001
        val job = launch(Dispatchers.Default) { engine.runDue() }
        assertTrue(firstCommandSent.await(10, TimeUnit.SECONDS))
        Thread.sleep(50)
        job.cancelAndJoin() // es. l'app va in background o cambiano le impostazioni

        assertEquals(1, snapCount(4001))
        assertEquals(0, snapCount(4002))
        val attempts = store.currentAttempts()
        assertTrue(attempts.containsKey("camera-4001"))
        assertTrue("4002 non deve risultare tentata", !attempts.containsKey("camera-4002"))
        assertTrue("4003 non deve risultare tentata", !attempts.containsKey("camera-4003"))

        // Il giro successivo riprende da dove si era fermato.
        releaseCommand.countDown()
        engine.runDue()
        assertEquals(1, snapCount(4001))
        assertEquals(1, snapCount(4002))
        assertEquals(1, snapCount(4003))
    }

    @Test
    fun `miniature lente ad arrivare vengono comunque scaricate`() = runBlocking {
        thumbnailDelayReads = 4 // la nuova immagine compare solo dopo alcune letture
        engine.runDue()
        cameraIds.forEach { id ->
            assertTrue("miniatura nuova per $id", cache.cachedUrl("camera-$id")!!.contains("ts=${thumbTs[id]}"))
            assertTrue(thumbTs[id]!! > oldTs)
        }
        assertEquals(3, engine.lastRun.value!!.updated.size)
    }

    @Test
    fun `disattivato non fa nulla`() = runBlocking {
        store.update { it.copy(enabled = false) }
        assertEquals(null, engine.runDue())
        cameraIds.forEach { assertEquals(0, snapCount(it)) }
    }
}
