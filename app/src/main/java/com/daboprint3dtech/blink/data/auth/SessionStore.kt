package com.daboprint3dtech.blink.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

/** Persistenza della sessione (cifrata) e dell'identificativo del dispositivo. */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Session? {
        val blob = dataStore.data.first()[SESSION] ?: return null
        val plain = cipher.decrypt(blob) ?: return null
        return runCatching { json.decodeFromString(Session.serializer(), plain) }.getOrNull()
    }

    suspend fun save(session: Session?) {
        dataStore.edit { prefs ->
            if (session == null) prefs.remove(SESSION)
            else prefs[SESSION] = cipher.encrypt(json.encodeToString(Session.serializer(), session))
        }
    }

    /** UUID maiuscolo e stabile per questa installazione (Blink rifiuta valori non-UUID). */
    suspend fun hardwareId(): String {
        dataStore.data.first()[HARDWARE_ID]?.let { return it }
        val id = UUID.randomUUID().toString().uppercase(Locale.ROOT)
        dataStore.edit { it[HARDWARE_ID] = id }
        return id
    }

    suspend fun lastEmail(): String? = dataStore.data.first()[LAST_EMAIL]

    suspend fun setLastEmail(email: String) {
        dataStore.edit { it[LAST_EMAIL] = email }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val HARDWARE_ID = stringPreferencesKey("hardware_id")
        val LAST_EMAIL = stringPreferencesKey("last_email")
    }
}
