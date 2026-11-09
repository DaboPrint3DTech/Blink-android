package com.daboprint3dtech.blink.data.api

import com.daboprint3dtech.blink.data.auth.AuthManager
import com.daboprint3dtech.blink.data.auth.Session
import com.daboprint3dtech.blink.testutil.tempSessionStore
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class BlinkApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BlinkApi
    private lateinit var auth: AuthManager
    private val requests = CopyOnWriteArrayList<String>()
    private var busyResponses = 0
    private var commandPolls = 0
    private var validToken = "AT"

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                requests += "${request.method} $path ${request.getHeader("Authorization")}"
                if (path == "/oauth/token") {
                    validToken = "AT2"
                    return MockResponse().setBody("""{"access_token":"AT2","refresh_token":"RT2","expires_in":3600}""")
                }
                if (request.getHeader("Authorization") != "Bearer $validToken") return MockResponse().setResponseCode(401)
                return when {
                    path == "/api/v3/accounts/4242/homescreen" -> MockResponse().setBody(HOMESCREEN_JSON)
                    path == "/network/2001/camera/4001/thumbnail" ->
                        if (busyResponses-- > 0) MockResponse().setResponseCode(409).setBody("""{"message":"System is busy","code":307}""")
                        else MockResponse().setBody("""{"id":99,"network_id":2001,"command":"thumbnail","complete":false}""")
                    path == "/network/2001/command/99" ->
                        MockResponse().setBody(if (++commandPolls < 3) """{"complete":false,"status_code":908}""" else """{"complete":true,"status_code":908}""")
                    path == "/api/v1/accounts/4242/networks/2002/owls/5001/config" -> MockResponse().setBody("{}")
                    path == "/api/v1/accounts/4242/networks/2001/state/arm" -> MockResponse().setBody("""{"id":100,"network_id":2001}""")
                    path == "/network/2001/command/100" -> MockResponse().setBody("""{"complete":true}""")
                    path == "/api/v1/accounts/4242/media/delete" -> MockResponse().setBody("""{"message":"ok"}""")
                    else -> MockResponse().setResponseCode(404).setBody("""{"message":"not found"}""")
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val store = tempSessionStore()
        store.save(Session("me@example.com", "AT", "RT", System.currentTimeMillis() + 3_600_000, "e006", 4242))
        auth = AuthManager(OAuthClient(OkHttpClient(), oauthBaseUrl = base), store)
        api = BlinkApi(
            OkHttpClient(),
            auth,
            baseUrlFor = { base },
            commandPollIntervalMs = 5,
            busyRetryDelayMs = 5,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `homescreen e url miniature`() = runTest {
        val home = api.homescreen()
        assertEquals(4, home.cameras.size)
        assertTrue(api.thumbnailUrl(home.camera("mini-5001")!!)!!.startsWith(server.url("/").toString().trimEnd('/')))
    }

    @Test
    fun `snapshot con sistema occupato e attesa del comando`() = runTest {
        busyResponses = 2
        val camera = api.homescreen().camera("camera-4001")!!
        api.requestThumbnail(camera)
        assertEquals(3, requests.count { it.startsWith("POST /network/2001/camera/4001/thumbnail") })
        assertEquals(3, commandPolls)
    }

    @Test
    fun `rilevamento movimento blink mini`() = runTest {
        val mini = api.homescreen().camera("mini-5001")!!
        api.setMotionDetection(mini, false)
        val req = server.takeRequestMatching("/owls/5001/config")
        assertEquals("""{"enabled":false}""", req)
    }

    @Test
    fun `arm del sistema e cancellazione clip`() = runTest {
        api.setSystemArmed(2001, true)
        api.deleteClips(listOf(11, 12))
        assertTrue(requests.any { it.startsWith("POST /api/v1/accounts/4242/networks/2001/state/arm") })
        assertEquals("""{"media_list":[11,12]}""", server.takeRequestMatching("/media/delete"))
    }

    @Test
    fun `token scaduto lato server viene rinnovato`() = runTest {
        validToken = "AT-other" // il server rifiuta AT → 401 → refresh → AT2
        api.homescreen()
        assertEquals("GET /api/v3/accounts/4242/homescreen Bearer AT", requests[0])
        assertTrue(requests[1].startsWith("POST /oauth/token"))
        assertEquals("GET /api/v3/accounts/4242/homescreen Bearer AT2", requests[2])
        assertEquals("AT2", auth.validSession().accessToken)
    }

    private fun MockWebServer.takeRequestMatching(fragment: String): String {
        while (true) {
            val r = takeRequest()
            if (r.path!!.contains(fragment)) return r.body.readUtf8()
        }
    }
}
