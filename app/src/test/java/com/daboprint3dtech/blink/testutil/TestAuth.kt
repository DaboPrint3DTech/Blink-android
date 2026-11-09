package com.daboprint3dtech.blink.testutil

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.daboprint3dtech.blink.data.auth.SessionStore
import com.daboprint3dtech.blink.data.auth.TokenCipher
import java.io.File
import java.nio.file.Files

/** Cifratura fittizia per i test JVM (l'Android Keystore non è disponibile). */
object PlainCipher : TokenCipher {
    override fun encrypt(plain: String) = "plain:$plain"
    override fun decrypt(encoded: String) = encoded.removePrefix("plain:")
}

fun tempSessionStore(): SessionStore {
    val dir = Files.createTempDirectory("session").toFile()
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(dir, "session.preferences_pb") })
    return SessionStore(dataStore, PlainCipher)
}
