package com.daboprint3dtech.blink.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

/** Persistenza delle impostazioni di aggiornamento e dell'ultimo tentativo per telecamera. */
class AutoRefreshStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val attemptsSerializer = MapSerializer(String.serializer(), Long.serializer())

    val settings: Flow<AutoRefreshSettings> = dataStore.data
        .map { prefs -> decodeSettings(prefs[SETTINGS]) }
        .distinctUntilChanged()

    /** Ultimo tentativo di aggiornamento automatico per telecamera (epoch millis). */
    val lastAttempts: Flow<Map<String, Instant>> = dataStore.data
        .map { prefs -> decodeAttempts(prefs[ATTEMPTS]) }
        .distinctUntilChanged()

    suspend fun current(): AutoRefreshSettings = settings.first()

    suspend fun currentAttempts(): Map<String, Instant> = lastAttempts.first()

    suspend fun update(transform: (AutoRefreshSettings) -> AutoRefreshSettings): AutoRefreshSettings {
        var result = AutoRefreshSettings()
        dataStore.edit { prefs ->
            result = transform(decodeSettings(prefs[SETTINGS]))
            prefs[SETTINGS] = json.encodeToString(AutoRefreshSettings.serializer(), result)
        }
        return result
    }

    suspend fun recordAttempts(keys: Collection<String>, at: Instant) {
        if (keys.isEmpty()) return
        dataStore.edit { prefs ->
            val current = decodeAttempts(prefs[ATTEMPTS]).mapValues { it.value.toEpochMilli() }
            val updated = current + keys.associateWith { at.toEpochMilli() }
            prefs[ATTEMPTS] = json.encodeToString(attemptsSerializer, updated)
        }
    }

    suspend fun clearAttempts() {
        dataStore.edit { it.remove(ATTEMPTS) }
    }

    private fun decodeSettings(raw: String?): AutoRefreshSettings =
        raw?.let { runCatching { json.decodeFromString(AutoRefreshSettings.serializer(), it) }.getOrNull() }
            ?: AutoRefreshSettings()

    private fun decodeAttempts(raw: String?): Map<String, Instant> =
        raw?.let { runCatching { json.decodeFromString(attemptsSerializer, it) }.getOrNull() }
            ?.mapValues { Instant.ofEpochMilli(it.value) }
            .orEmpty()

    private companion object {
        val SETTINGS = stringPreferencesKey("auto_refresh_settings")
        val ATTEMPTS = stringPreferencesKey("auto_refresh_attempts")
    }
}
