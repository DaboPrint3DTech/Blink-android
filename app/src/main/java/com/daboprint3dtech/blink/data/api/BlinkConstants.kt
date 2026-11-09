package com.daboprint3dtech.blink.data.api

/**
 * Costanti dell'API (non ufficiale) di Blink.
 *
 * I valori ricalcano quelli usati dall'app ufficiale e documentati dal progetto
 * open source blinkpy (https://github.com/fronzbot/blinkpy). Blink può cambiarli
 * in qualsiasi momento: se il login smette di funzionare è il primo posto da controllare.
 */
object BlinkConstants {
    const val OAUTH_BASE_URL = "https://api.oauth.blink.com"
    const val TIER_URL = "https://rest-prod.immedia-semi.com/api/v1/users/tier_info"

    const val OAUTH_CLIENT_ID = "ios"
    const val OAUTH_SCOPE = "client"
    const val OAUTH_REDIRECT_URI = "immedia-blink://applinks.blink.com/signin/callback"

    const val APP_VERSION = "50.1"
    const val DEVICE_BRAND = "Apple"
    const val DEVICE_MODEL = "iPhone16,1"
    const val DEVICE_OS_VERSION = "26.1"

    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.1 Mobile/15E148 Safari/604.1"
    const val TOKEN_USER_AGENT = "Blink/2511191620 CFNetwork/3860.200.71 Darwin/25.1.0"

    /** Host REST per la regione ("tier") dell'account, es. `e006` → rest-e006.immedia-semi.com. */
    fun restBaseUrl(tier: String) = "https://rest-$tier.immedia-semi.com"
}
