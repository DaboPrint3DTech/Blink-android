package com.daboprint3dtech.blink.data.auth

import kotlinx.serialization.Serializable

/** Sessione autenticata con Blink. Il refresh token permette di non salvare mai la password. */
@Serializable
data class Session(
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    /** Scadenza dell'access token (epoch millis). */
    val expiresAtMillis: Long,
    val tier: String,
    val accountId: Long,
) {
    fun isExpiring(nowMillis: Long = System.currentTimeMillis()) = expiresAtMillis - nowMillis < 60_000
}
