package com.daboprint3dtech.blink.data.api

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Token restituiti dal server OAuth di Blink. */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

/** Regione ("tier") e id dell'account Blink. */
data class TierInfo(
    val tier: String,
    val accountId: Long,
)

/** Stato intermedio del login quando Blink chiede il codice di verifica (2FA). */
class PendingTwoFactor internal constructor(
    internal val csrfToken: String,
    internal val codeVerifier: String,
    internal val hardwareId: String,
    internal val cookieJar: MemoryCookieJar,
    val email: String,
)

sealed interface SignInResult {
    data class Success(val tokens: TokenResponse) : SignInResult
    data class TwoFactorRequired(val pending: PendingTwoFactor) : SignInResult
}

/**
 * Implementa il login OAuth 2.0 "Authorization Code + PKCE" usato dall'app Blink
 * (stesso flusso di blinkpy):
 *
 * 1. GET  /oauth/v2/authorize (con code_challenge) → avvia la sessione (cookie)
 * 2. GET  /oauth/v2/signin → pagina HTML da cui estrarre il token CSRF
 * 3. POST /oauth/v2/signin (email, password, csrf) → 3xx = ok, 412/202 = serve 2FA
 * 3b.POST /oauth/v2/2fa/verify (codice ricevuto via SMS/email)
 * 4. GET  /oauth/v2/authorize → redirect con `code`
 * 5. POST /oauth/token (code + code_verifier) → access/refresh token
 */
class OAuthClient(
    private val baseClient: OkHttpClient,
    private val oauthBaseUrl: String = BlinkConstants.OAUTH_BASE_URL,
    private val tierUrl: String = BlinkConstants.TIER_URL,
) {
    private val authorizeUrl get() = "$oauthBaseUrl/oauth/v2/authorize"
    private val signinUrl get() = "$oauthBaseUrl/oauth/v2/signin"
    private val twoFaUrl get() = "$oauthBaseUrl/oauth/v2/2fa/verify"
    private val tokenUrl get() = "$oauthBaseUrl/oauth/token"

    suspend fun signIn(email: String, password: String, hardwareId: String): SignInResult {
        val jar = MemoryCookieJar()
        val (follow, noFollow) = clients(jar)
        val pkce = Pkce.generate()

        // 1. Authorization request
        val authUrl = authorizeUrl.toHttpUrl().newBuilder()
            .addQueryParameter("app_brand", "blink")
            .addQueryParameter("app_version", BlinkConstants.APP_VERSION)
            .addQueryParameter("client_id", BlinkConstants.OAUTH_CLIENT_ID)
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("device_brand", BlinkConstants.DEVICE_BRAND)
            .addQueryParameter("device_model", BlinkConstants.DEVICE_MODEL)
            .addQueryParameter("device_os_version", BlinkConstants.DEVICE_OS_VERSION)
            .addQueryParameter("hardware_id", hardwareId)
            .addQueryParameter("redirect_uri", BlinkConstants.OAUTH_REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", BlinkConstants.OAUTH_SCOPE)
            .build()
        follow.newCall(browserRequest(authUrl.toString()).get().build()).await().use { r ->
            if (r.code != 200) throw BlinkLoginException("Richiesta di autorizzazione rifiutata (HTTP ${r.code})")
        }

        // 2. Pagina di login → token CSRF
        val csrf = follow.newCall(browserRequest(signinUrl).get().build()).await().use { r ->
            if (r.code != 200) throw BlinkLoginException("Pagina di login non disponibile (HTTP ${r.code})")
            extractCsrfToken(r.body?.string().orEmpty())
        } ?: throw BlinkLoginException("Token CSRF non trovato nella pagina di login")

        // 3. Invio credenziali
        val form = FormBody.Builder()
            .add("username", email)
            .add("password", password)
            .add("csrf-token", csrf)
            .build()
        val signinRequest = Request.Builder().url(signinUrl)
            .header("User-Agent", BlinkConstants.BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", oauthBaseUrl)
            .header("Referer", signinUrl)
            .post(form)
            .build()
        val outcome = noFollow.newCall(signinRequest).await().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 412 -> SignInOutcome.TWO_FACTOR
                r.code == 202 && looksLikeTwoFactor(body) -> SignInOutcome.TWO_FACTOR
                r.code in 300..399 -> SignInOutcome.SUCCESS
                r.code == 401 || r.code == 403 || r.code == 422 ->
                    throw BlinkLoginException("Email o password non corretti")
                else -> throw BlinkLoginException(
                    "Login non riuscito (HTTP ${r.code})" + body.take(200).let { if (it.isBlank()) "" else ": $it" },
                )
            }
        }

        val pendingState = PendingTwoFactor(csrf, pkce.verifier, hardwareId, jar, email)
        if (outcome == SignInOutcome.TWO_FACTOR) return SignInResult.TwoFactorRequired(pendingState)
        return SignInResult.Success(finishLogin(pendingState))
    }

    /** Completa il login inviando il codice di verifica ricevuto da Blink. */
    suspend fun verifyTwoFactor(pending: PendingTwoFactor, code: String): TokenResponse {
        val (follow, _) = clients(pending.cookieJar)
        val form = FormBody.Builder()
            .add("2fa_code", code.trim())
            .add("csrf-token", pending.csrfToken)
            .add("remember_me", "false")
            .build()
        val request = Request.Builder().url(twoFaUrl)
            .header("User-Agent", BlinkConstants.BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", oauthBaseUrl)
            .header("Referer", signinUrl)
            .post(form)
            .build()
        follow.newCall(request).await().use { r ->
            val json = parseJsonObject(r.body?.string())
            val ok = r.code in 200..299 && json?.str("status") == "auth-completed"
            if (!ok) throw BlinkLoginException("Codice di verifica non valido o scaduto")
        }
        return finishLogin(pending)
    }

    /** Passi 4-5: ottiene il codice di autorizzazione e lo scambia con i token. */
    private suspend fun finishLogin(state: PendingTwoFactor): TokenResponse {
        val (_, noFollow) = clients(state.cookieJar)
        val request = Request.Builder().url(authorizeUrl)
            .header("User-Agent", BlinkConstants.BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", signinUrl)
            .get()
            .build()
        val code = noFollow.newCall(request).await().use { r ->
            if (r.code !in 300..399) null else r.header("Location")?.let { queryParameter(it, "code") }
        } ?: throw BlinkLoginException("Codice di autorizzazione non ricevuto")

        val form = FormBody.Builder()
            .add("app_brand", "blink")
            .add("client_id", BlinkConstants.OAUTH_CLIENT_ID)
            .add("code", code)
            .add("code_verifier", state.codeVerifier)
            .add("grant_type", "authorization_code")
            .add("hardware_id", state.hardwareId)
            .add("redirect_uri", BlinkConstants.OAUTH_REDIRECT_URI)
            .add("scope", BlinkConstants.OAUTH_SCOPE)
            .build()
        return postToken(form) ?: throw BlinkLoginException("Scambio del codice con il token non riuscito")
    }

    /** Rinnova l'access token. Lancia [BlinkAuthException] se il refresh token non è più valido. */
    suspend fun refresh(refreshToken: String, hardwareId: String): TokenResponse {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", BlinkConstants.OAUTH_CLIENT_ID)
            .add("scope", BlinkConstants.OAUTH_SCOPE)
            .add("hardware_id", hardwareId)
            .build()
        return postToken(form, throwAuthOnClientError = true)
            ?: throw BlinkApiException(500, "Rinnovo del token non riuscito")
    }

    suspend fun tierInfo(accessToken: String): TierInfo {
        val request = Request.Builder().url(tierUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", BlinkConstants.TOKEN_USER_AGENT)
            .get()
            .build()
        return baseClient.newCall(request).await().use { r ->
            val json = parseJsonObject(r.body?.string())
            val tier = json?.str("tier")
            val account = json?.long("account_id")
            if (r.code !in 200..299 || tier.isNullOrBlank() || account == null) {
                throw BlinkLoginException("Impossibile determinare la regione dell'account (HTTP ${r.code})")
            }
            TierInfo(tier, account)
        }
    }

    private suspend fun postToken(form: FormBody, throwAuthOnClientError: Boolean = false): TokenResponse? {
        val request = Request.Builder().url(tokenUrl)
            .header("User-Agent", BlinkConstants.TOKEN_USER_AGENT)
            .header("Accept", "*/*")
            .post(form)
            .build()
        return baseClient.newCall(request).await().use { r ->
            val json = parseJsonObject(r.body?.string())
            if (r.code == 200 && json != null) {
                val access = json.str("access_token")
                val refresh = json.str("refresh_token")
                if (access != null && refresh != null) {
                    return@use TokenResponse(access, refresh, json.long("expires_in") ?: 3600L)
                }
            }
            if (throwAuthOnClientError && r.code in 400..499) {
                throw BlinkAuthException("Sessione scaduta, effettua di nuovo l'accesso")
            }
            null
        }
    }

    private fun clients(jar: MemoryCookieJar): Pair<OkHttpClient, OkHttpClient> {
        val follow = baseClient.newBuilder().cookieJar(jar).followRedirects(true).followSslRedirects(true).build()
        val noFollow = follow.newBuilder().followRedirects(false).followSslRedirects(false).build()
        return follow to noFollow
    }

    private fun browserRequest(url: String) = Request.Builder().url(url)
        .header("User-Agent", BlinkConstants.BROWSER_USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")

    private enum class SignInOutcome { SUCCESS, TWO_FACTOR }

    companion object {
        private val scriptRegex = Regex("<script\\b([^>]*)>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val oauthArgsId = Regex("id\\s*=\\s*[\"']oauth-args[\"']", RegexOption.IGNORE_CASE)

        /** Estrae il token CSRF dal tag `<script id="oauth-args" type="application/json">`. */
        internal fun extractCsrfToken(html: String): String? = scriptRegex.findAll(html)
            .filter { oauthArgsId.containsMatchIn(it.groupValues[1]) }
            .firstNotNullOfOrNull { parseJsonObject(it.groupValues[2].trim())?.str("csrf-token") }

        internal fun looksLikeTwoFactor(body: String): Boolean {
            val json = parseJsonObject(body) ?: return false
            return json["tsv_state"] != null || json["tsv_methods"] != null || json["next_time_in_secs"] != null
        }

        /** Legge un parametro dalla query di un URL (anche con schema custom, es. immedia-blink://). */
        internal fun queryParameter(url: String, name: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            return query.split('&').firstNotNullOfOrNull { pair ->
                val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                if (key == name) URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") else null
            }
        }
    }
}

/** Proof Key for Code Exchange (RFC 7636). */
internal object Pkce {
    data class Pair(val verifier: String, val challenge: String)

    private val random = SecureRandom()

    fun generate(): Pair {
        val bytes = ByteArray(32).also(random::nextBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return Pair(verifier, challengeFor(verifier))
    }

    fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
