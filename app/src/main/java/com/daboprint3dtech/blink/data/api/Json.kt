package com.daboprint3dtech.blink.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Le risposte di Blink cambiano spesso forma: invece di classi rigide si naviga
 * il JSON in modo tollerante, ignorando campi mancanti o di tipo inatteso.
 */
internal val BlinkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

internal fun parseJsonObject(text: String?): JsonObject? {
    if (text.isNullOrBlank()) return null
    return runCatching { BlinkJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.str(key: String): String? = primitive(key)?.contentOrNull

internal fun JsonObject.long(key: String): Long? =
    primitive(key)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }

internal fun JsonObject.int(key: String): Int? =
    primitive(key)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.let { p ->
    p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: when (p.contentOrNull?.lowercase()) {
        "true", "yes", "on", "enabled" -> true
        "false", "no", "off", "disabled" -> false
        else -> null
    }
}

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
