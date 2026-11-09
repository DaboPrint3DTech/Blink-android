package com.daboprint3dtech.blink.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/** Simula i server OAuth di Blink e verifica l'intero flusso di login con PKCE e 2FA. */
class OAuthClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OAuthClient

    private var challenge: String? = null
    private var signinStatus = 412
    private var cookieSeenOnSignin = false
    private val tokenForms = mutableListOf<Map<String, String>>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        client = OAuthClient(OkHttpClient(), oauthBaseUrl = base, tierUrl = "$base/api/v1/users/tier_info")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun form(r: RecordedRequest): Map<String, String> = r.body.readUtf8().split('&').filter { it.isNotEmpty() }
        .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private fun handle(r: RecordedRequest): MockResponse {
        val path = r.requestUrl!!.encodedPath
        return when {
            r.method == "GET" && path == "/oauth/v2/authorize" && r.requestUrl!!.queryParameter("code_challenge") != null -> {
                challenge = r.requestUrl!!.queryParameter("code_challenge")
                assertEquals("S256", r.requestUrl!!.queryParameter("code_challenge_method"))
                assertEquals("ios", r.requestUrl!!.queryParameter("client_id"))
                assertEquals("HW-1", r.requestUrl!!.queryParameter("hardware_id"))
                MockResponse().setResponseCode(302).addHeader("Location", "/oauth/v2/signin")
                    .addHeader("Set-Cookie", "sess=abc; Path=/")
            }
            r.method == "GET" && path == "/oauth/v2/signin" -> {
                cookieSeenOnSignin = r.getHeader("Cookie")?.contains("sess=abc") == true
                MockResponse().setBody(
                    """<html><head><script src="x.js"></script>
                       <script type="application/json" id="oauth-args">{"csrf-token":"tok123","other":1}</script>
                       </head><body>login</body></html>""",
                )
            }
            r.method == "POST" && path == "/oauth/v2/signin" -> {
                val f = form(r)
                assertEquals("tok123", f["csrf-token"])
                if (f["password"] != "secret") return MockResponse().setResponseCode(401)
                assertTrue(r.getHeader("Cookie")!!.contains("sess=abc"))
                if (signinStatus == 302) {
                    MockResponse().setResponseCode(302).addHeader("Location", "/oauth/v2/authorize")
                } else {
                    MockResponse().setResponseCode(signinStatus).setBody("""{"tsv_state":"pending"}""")
                }
            }
            r.method == "POST" && path == "/oauth/v2/2fa/verify" -> {
                val f = form(r)
                assertEquals("tok123", f["csrf-token"])
                if (f["2fa_code"] == "123456") {
                    MockResponse().setResponseCode(201).setBody("""{"status":"auth-completed"}""")
                } else {
                    MockResponse().setResponseCode(400).setBody("""{"status":"error"}""")
                }
            }
            r.method == "GET" && path == "/oauth/v2/authorize" ->
                MockResponse().setResponseCode(302)
                    .addHeader("Location", "immedia-blink://applinks.blink.com/signin/callback?code=CODE%2042&state=s")
            r.method == "POST" && path == "/oauth/token" -> {
                val f = form(r)
                tokenForms += f
                when (f["grant_type"]) {
                    "authorization_code" -> {
                        assertEquals("CODE 42", f["code"])
                        assertEquals(challenge, Pkce.challengeFor(f["code_verifier"]!!))
                        MockResponse().setBody("""{"access_token":"AT","refresh_token":"RT","expires_in":14400,"token_type":"Bearer"}""")
                    }
                    "refresh_token" ->
                        if (f["refresh_token"] == "RT") MockResponse().setBody("""{"access_token":"AT2","refresh_token":"RT2","expires_in":3600}""")
                        else MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant"}""")
                    else -> MockResponse().setResponseCode(400)
                }
            }
            r.method == "GET" && path == "/api/v1/users/tier_info" -> {
                assertEquals("Bearer AT", r.getHeader("Authorization"))
                MockResponse().setBody("""{"tier":"e006","account_id":4242}""")
            }
            else -> MockResponse().setResponseCode(404)
        }
    }

    @Test
    fun `login con verifica in due passaggi`() = runTest {
        val first = client.signIn("me@example.com", "secret", "HW-1")
        assertTrue(cookieSeenOnSignin)
        val pending = (first as SignInResult.TwoFactorRequired).pending

        val tokens = client.verifyTwoFactor(pending, " 123456 ")
        assertEquals(TokenResponse("AT", "RT", 14400), tokens)
        assertEquals("HW-1", tokenForms.single()["hardware_id"])
        assertEquals(TierInfo("e006", 4242), client.tierInfo(tokens.accessToken))
    }

    @Test
    fun `login senza 2FA`() = runTest {
        signinStatus = 302
        val result = client.signIn("me@example.com", "secret", "HW-1")
        assertEquals("AT", (result as SignInResult.Success).tokens.accessToken)
    }

    @Test
    fun `2FA anche con risposta 202`() = runTest {
        signinStatus = 202
        assertTrue(client.signIn("me@example.com", "secret", "HW-1") is SignInResult.TwoFactorRequired)
    }

    @Test
    fun `password errata`() = runTest {
        try {
            client.signIn("me@example.com", "wrong", "HW-1")
            fail("attesa eccezione")
        } catch (e: BlinkLoginException) {
            assertEquals("Email o password non corretti", e.message)
        }
    }

    @Test
    fun `codice 2FA errato`() = runTest {
        val pending = (client.signIn("me@example.com", "secret", "HW-1") as SignInResult.TwoFactorRequired).pending
        try {
            client.verifyTwoFactor(pending, "000000")
            fail("attesa eccezione")
        } catch (e: BlinkLoginException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `refresh token`() = runTest {
        assertEquals("AT2", client.refresh("RT", "HW-1").accessToken)
        try {
            client.refresh("expired", "HW-1")
            fail("attesa eccezione")
        } catch (e: BlinkAuthException) {
            // ok: serve un nuovo login
        }
    }

    @Test
    fun `estrazione csrf e parametri`() {
        val html = """<script id='oauth-args' type="application/json">
            {"csrf-token": "abc"}
        </script>"""
        assertEquals("abc", OAuthClient.extractCsrfToken(html))
        assertEquals(null, OAuthClient.extractCsrfToken("<script>{\"csrf-token\":\"x\"}</script>"))
        assertEquals("a b", OAuthClient.queryParameter("immedia-blink://x/cb?state=1&code=a%20b#frag", "code"))
        assertEquals(null, OAuthClient.queryParameter("immedia-blink://x/cb", "code"))
    }

    @Test
    fun `pkce rfc 7636`() {
        assertEquals("7PMFfCoAJhDlDP0meV7JbFLuFwfgBA9EL44QVCW_vSs", Pkce.challengeFor("dBjftJeZ4CVP-mJ92K9qXe3pDz0yPq5uTwR5oZ6GV4lQ"))
        val p = Pkce.generate()
        assertEquals(43, p.verifier.length)
        assertEquals(p.challenge, Pkce.challengeFor(p.verifier))
    }
}
