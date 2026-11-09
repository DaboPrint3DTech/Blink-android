package com.daboprint3dtech.blink.data.api

import com.daboprint3dtech.blink.data.auth.AuthManager
import com.daboprint3dtech.blink.data.auth.Session
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Client REST per le API Blink (rest-{tier}.immedia-semi.com).
 * Ogni chiamata usa un access token valido e, in caso di 401, rinnova il token e ritenta una volta.
 */
class BlinkApi(
    private val http: OkHttpClient,
    private val auth: AuthManager,
    private val baseUrlFor: (Session) -> String = { BlinkConstants.restBaseUrl(it.tier) },
    private val commandPollIntervalMs: Long = 1_000,
    private val busyRetryDelayMs: Long = 5_000,
    private val commandTimeoutMs: Long = 60_000,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- lettura stato

    suspend fun homescreen(): Homescreen =
        Homescreen.parse(getJson { s -> "${base(s)}/api/v3/accounts/${s.accountId}/homescreen" })

    /** Clip registrate dopo [since], una pagina (~25 elementi) alla volta; pagine da 1. */
    suspend fun clips(since: Instant, page: Int): List<Clip> {
        val sinceParam = since.truncatedTo(ChronoUnit.SECONDS).toString()
        return Clip.parseList(
            getJson { s -> "${base(s)}/api/v1/accounts/${s.accountId}/media/changed?since=$sinceParam&page=$page" },
        )
    }

    suspend fun deleteClips(ids: List<Long>) {
        val body = buildJsonObject { put("media_list", JsonArray(ids.map { JsonPrimitive(it) })) }
        postJson(body.toString()) { s -> "${base(s)}/api/v1/accounts/${s.accountId}/media/delete" }
    }

    // ---------------------------------------------------------------- comandi

    /** Inserisce/disinserisce un sistema (equivale al pulsante "Armato/Disarmato" dell'app). */
    suspend fun setSystemArmed(networkId: Long, armed: Boolean) {
        val action = if (armed) "arm" else "disarm"
        runCommand { postJson { s -> "${base(s)}/api/v1/accounts/${s.accountId}/networks/$networkId/state/$action" } }
    }

    /** Attiva/disattiva il rilevamento movimento della singola telecamera. */
    suspend fun setMotionDetection(camera: BlinkCamera, enabled: Boolean) {
        val action = if (enabled) "enable" else "disable"
        runCommand {
            when (camera.kind) {
                DeviceKind.CAMERA -> postJson { s -> "${base(s)}/network/${camera.networkId}/camera/${camera.id}/$action" }
                DeviceKind.DOORBELL -> postJson { s -> "${deviceBase(s, camera)}/$action" }
                DeviceKind.MINI -> postJson("""{"enabled":$enabled}""") { s -> "${deviceBase(s, camera)}/config" }
            }
        }
    }

    /** Chiede alla telecamera di scattare una nuova miniatura e attende il completamento. */
    suspend fun requestThumbnail(camera: BlinkCamera) {
        runCommand {
            when (camera.kind) {
                DeviceKind.CAMERA -> postJson { s -> "${base(s)}/network/${camera.networkId}/camera/${camera.id}/thumbnail" }
                else -> postJson { s -> "${deviceBase(s, camera)}/thumbnail" }
            }
        }
    }

    /** Avvia la registrazione di una clip. */
    suspend fun requestClip(camera: BlinkCamera) {
        runCommand {
            when (camera.kind) {
                DeviceKind.CAMERA -> postJson { s -> "${base(s)}/network/${camera.networkId}/camera/${camera.id}/clip" }
                else -> postJson { s -> "${deviceBase(s, camera)}/clip" }
            }
        }
    }

    // ---------------------------------------------------------------- media

    suspend fun thumbnailUrl(camera: BlinkCamera): String? {
        val s = auth.validSession()
        return BlinkUrls.thumbnailUrl(base(s), s.accountId, camera)
    }

    suspend fun clipThumbnailUrl(clip: Clip): String? = BlinkUrls.clipThumbnailUrl(base(auth.validSession()), clip)

    suspend fun clipVideoUrl(clip: Clip): String = BlinkUrls.clipVideoUrl(base(auth.validSession()), clip)

    /** Header necessari per scaricare/riprodurre i media (es. dal player video). */
    suspend fun mediaHeaders(): Map<String, String> =
        mapOf("Authorization" to "Bearer ${auth.validSession().accessToken}")

    /** Scarica un file (miniatura/clip) autenticato. */
    suspend fun download(url: String): ByteArray = execute(
        build = { s -> Request.Builder().url(url).get().authorized(s) },
        parse = { code, bytes ->
            if (code !in 200..299) throw BlinkApiException(code, "Download non riuscito (HTTP $code)")
            bytes
        },
    )

    // ---------------------------------------------------------------- infrastruttura

    private fun base(s: Session) = baseUrlFor(s).trimEnd('/')

    private fun deviceBase(s: Session, camera: BlinkCamera): String {
        val segment = if (camera.kind == DeviceKind.MINI) "owls" else "doorbells"
        return "${base(s)}/api/v1/accounts/${s.accountId}/networks/${camera.networkId}/$segment/${camera.id}"
    }

    private fun Request.Builder.authorized(s: Session) = header("Authorization", "Bearer ${s.accessToken}")

    private suspend fun getJson(url: (Session) -> String): JsonObject = execute(
        build = { s -> Request.Builder().url(url(s)).get().authorized(s) },
        parse = ::jsonOrThrow,
    )

    private suspend fun postJson(body: String? = null, url: (Session) -> String): JsonObject = execute(
        build = { s ->
            val requestBody: RequestBody = (body ?: "").toRequestBody(jsonType)
            Request.Builder().url(url(s)).post(requestBody).authorized(s)
        },
        parse = ::jsonOrThrow,
    )

    private fun jsonOrThrow(code: Int, bytes: ByteArray): JsonObject {
        val json = parseJsonObject(bytes.toString(Charsets.UTF_8))
        if (code in 200..299) return json ?: JsonObject(emptyMap())
        val message = json?.str("message") ?: "Errore del server Blink"
        if (isBusy(json)) throw BlinkBusyException(message)
        throw BlinkApiException(code, "$message (HTTP $code)")
    }

    private suspend fun <T> execute(build: (Session) -> Request.Builder, parse: (Int, ByteArray) -> T): T {
        var session = auth.validSession()
        repeat(2) { attempt ->
            val response = http.newCall(build(session).build()).await()
            val (code, bytes) = response.use { it.code to (it.body?.bytes() ?: ByteArray(0)) }
            if (code == 401 && attempt == 0) {
                session = auth.onUnauthorized(session.accessToken)
                return@repeat
            }
            if (code == 401) throw BlinkAuthException("Sessione non valida, effettua di nuovo l'accesso")
            return parse(code, bytes)
        }
        error("unreachable")
    }

    /**
     * Esegue un comando verso il sistema: se è occupato riprova ogni 5 s, poi
     * attende che il comando risulti completato interrogando /network/{id}/command/{id}.
     */
    private suspend fun runCommand(send: suspend () -> JsonObject): JsonObject {
        val start = System.currentTimeMillis()
        var response: JsonObject
        while (true) {
            response = try {
                send()
            } catch (e: BlinkBusyException) {
                if (System.currentTimeMillis() - start > commandTimeoutMs) throw e
                delay(busyRetryDelayMs)
                continue
            }
            if (isBusy(response)) {
                if (System.currentTimeMillis() - start > commandTimeoutMs) {
                    throw BlinkBusyException(response.str("message") ?: "Sistema occupato")
                }
                delay(busyRetryDelayMs)
                continue
            }
            break
        }
        val commandId = response.long("id") ?: response.long("command_id")
        val networkId = response.long("network_id")
        if (commandId != null && networkId != null) waitForCommand(networkId, commandId, start)
        return response
    }

    private suspend fun waitForCommand(networkId: Long, commandId: Long, start: Long) {
        while (System.currentTimeMillis() - start < commandTimeoutMs) {
            val status = runCatching {
                getJson { s -> "${base(s)}/network/$networkId/command/$commandId" }
            }.getOrNull()
            if (status?.bool("complete") == true) return
            delay(commandPollIntervalMs)
        }
        // Timeout: il comando potrebbe comunque andare a buon fine più tardi; non è un errore bloccante.
    }

    private fun isBusy(json: JsonObject?): Boolean {
        if (json == null) return false
        val message = json.str("message").orEmpty()
        return message.contains("busy", ignoreCase = true) || json.int("code") == 307
    }
}
