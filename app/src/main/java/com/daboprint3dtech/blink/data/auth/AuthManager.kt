package com.daboprint3dtech.blink.data.auth

import com.daboprint3dtech.blink.data.api.BlinkAuthException
import com.daboprint3dtech.blink.data.api.BlinkConstants
import com.daboprint3dtech.blink.data.api.OAuthClient
import com.daboprint3dtech.blink.data.api.PendingTwoFactor
import com.daboprint3dtech.blink.data.api.SignInResult
import com.daboprint3dtech.blink.data.api.TokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stato di autenticazione osservato dalla UI. */
sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: Session) : AuthState
}

sealed interface LoginStep {
    data object Done : LoginStep
    data class NeedsTwoFactor(val pending: PendingTwoFactor) : LoginStep
}

/**
 * Gestisce login, rinnovo automatico dei token e logout.
 * Un mutex evita rinnovi concorrenti (UI e lavoro in background condividono la sessione).
 */
class AuthManager(
    private val oauth: OAuthClient,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var session: Session? = null
    private var loaded = false

    suspend fun init() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        session = store.load()
        loaded = true
        publish()
    }

    private fun publish() {
        _state.value = session?.let { AuthState.LoggedIn(it) } ?: AuthState.LoggedOut
    }

    suspend fun lastEmail(): String? = store.lastEmail()

    suspend fun login(email: String, password: String): LoginStep {
        val cleanEmail = email.trim()
        store.setLastEmail(cleanEmail)
        return when (val result = oauth.signIn(cleanEmail, password, store.hardwareId())) {
            is SignInResult.Success -> {
                completeLogin(cleanEmail, result.tokens)
                LoginStep.Done
            }
            is SignInResult.TwoFactorRequired -> LoginStep.NeedsTwoFactor(result.pending)
        }
    }

    suspend fun verifyTwoFactor(pending: PendingTwoFactor, code: String) {
        val tokens = oauth.verifyTwoFactor(pending, code)
        completeLogin(pending.email, tokens)
    }

    private suspend fun completeLogin(email: String, tokens: TokenResponse) {
        val tier = oauth.tierInfo(tokens.accessToken)
        val newSession = Session(
            email = email,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMillis = clock() + tokens.expiresInSeconds * 1000,
            tier = tier.tier,
            accountId = tier.accountId,
        )
        mutex.withLock {
            loaded = true
            session = newSession
            store.save(newSession)
            publish()
        }
    }

    suspend fun logout() = mutex.withLock {
        session = null
        loaded = true
        store.save(null)
        publish()
    }

    /** Sessione con un access token valido (rinnovato se in scadenza). */
    suspend fun validSession(): Session = mutex.withLock {
        ensureLoaded()
        val current = session ?: throw BlinkAuthException("Accesso non effettuato")
        if (current.isExpiring(clock())) refreshLocked(current) else current
    }

    /** Da chiamare quando il server risponde 401 con [failedToken]. */
    suspend fun onUnauthorized(failedToken: String): Session = mutex.withLock {
        ensureLoaded()
        val current = session ?: throw BlinkAuthException("Accesso non effettuato")
        if (current.accessToken != failedToken) current else refreshLocked(current)
    }

    private suspend fun refreshLocked(current: Session): Session {
        val tokens = try {
            oauth.refresh(current.refreshToken, store.hardwareId())
        } catch (e: BlinkAuthException) {
            session = null
            store.save(null)
            publish()
            throw e
        }
        val updated = current.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMillis = clock() + tokens.expiresInSeconds * 1000,
        )
        session = updated
        store.save(updated)
        publish()
        return updated
    }

    fun baseUrl(session: Session) = BlinkConstants.restBaseUrl(session.tier)
}
