package com.daboprint3dtech.blink.data.api

import java.io.IOException

/** Errore restituito dai server Blink (codice HTTP non 2xx). */
open class BlinkApiException(
    val httpCode: Int,
    override val message: String,
) : IOException(message)

/** Le credenziali (o il refresh token) non sono più valide: serve un nuovo login. */
class BlinkAuthException(message: String) : BlinkApiException(401, message)

/** Errore durante il flusso di login OAuth. */
class BlinkLoginException(message: String) : IOException(message)

/** Il sistema è occupato da un altro comando e non si è liberato entro il timeout. */
class BlinkBusyException(message: String) : BlinkApiException(409, message)
