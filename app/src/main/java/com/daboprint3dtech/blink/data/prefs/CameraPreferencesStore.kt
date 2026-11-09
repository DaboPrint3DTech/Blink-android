package com.daboprint3dtech.blink.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Persistenza delle preferenze telecamere e dello stato "già avvisato" per la batteria. */
class CameraPreferencesStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keySet = SetSerializer(String.serializer())

    val preferences: Flow<CameraPreferences> = dataStore.data
        .map { decode(it[PREFS]) }
        .distinctUntilChanged()

    suspend fun current(): CameraPreferences = preferences.first()

    suspend fun update(transform: (CameraPreferences) -> CameraPreferences): CameraPreferences {
        var result = CameraPreferences()
        dataStore.edit { prefs ->
            result = transform(decode(prefs[PREFS]))
            prefs[PREFS] = json.encodeToString(CameraPreferences.serializer(), result)
        }
        return result
    }

    /** Chiavi delle telecamere per cui è già stato mostrato l'avviso di batteria scarica. */
    suspend fun alertedKeys(): Set<String> =
        dataStore.data.first()[ALERTED]?.let { runCatching { json.decodeFromString(keySet, it) }.getOrNull() }.orEmpty()

    suspend fun setAlertedKeys(keys: Set<String>) {
        dataStore.edit { it[ALERTED] = json.encodeToString(keySet, keys) }
    }

    private fun decode(raw: String?): CameraPreferences =
        raw?.let { runCatching { json.decodeFromString(CameraPreferences.serializer(), it) }.getOrNull() }
            ?: CameraPreferences()

    private companion object {
        val PREFS = stringPreferencesKey("camera_preferences")
        val ALERTED = stringPreferencesKey("battery_alerted")
    }
}
