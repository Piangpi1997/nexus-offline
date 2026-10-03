package com.nexusoffline

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Key access contract; production keys are generated and retained only by Android Keystore. */
internal interface LocalAIModelKeyProvider {
    fun create(keyId: String): SecretKey
    fun get(keyId: String): SecretKey?
    fun delete(keyId: String): Boolean
    fun deleteOrphaned(activeKeyIds: Set<String>): Boolean
    fun deleteAll(): Boolean
}

/** One nonexportable 256-bit AES key per imported model container. */
internal class AndroidLocalAIModelKeyProvider : LocalAIModelKeyProvider {
    override fun create(keyId: String): SecretKey {
        require(KEY_ID.matches(keyId)) { "BAD_MODEL_KEY_ID" }
        val store = keyStore()
        val alias = alias(keyId)
        if (store.containsAlias(alias)) throw IllegalStateException("MODEL_KEY_ALREADY_EXISTS")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // The container owns nonce allocation: one random prefix plus a unique counter per
                // chunk, with a fresh Keystore key for every container.
                .setRandomizedEncryptionRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    override fun get(keyId: String): SecretKey? {
        require(KEY_ID.matches(keyId)) { "BAD_MODEL_KEY_ID" }
        val key = keyStore().getKey(alias(keyId), null) as? SecretKey ?: return null
        // AndroidKeyStore keys are nonexportable; never request or persist encoded key bytes.
        return key
    }

    override fun delete(keyId: String): Boolean {
        require(KEY_ID.matches(keyId)) { "BAD_MODEL_KEY_ID" }
        return try {
            val store = keyStore()
            val alias = alias(keyId)
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            !store.containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteOrphaned(activeKeyIds: Set<String>): Boolean = try {
        val store = keyStore()
        val aliases = store.aliases().toList().filter { it.startsWith(ALIAS_PREFIX) }
        aliases.all { alias ->
            val keyId = alias.removePrefix(ALIAS_PREFIX)
            if (keyId in activeKeyIds) true else {
                store.deleteEntry(alias)
                !store.containsAlias(alias)
            }
        }
    } catch (_: Exception) {
        false
    }

    override fun deleteAll(): Boolean = deleteOrphaned(emptySet())

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun alias(keyId: String): String = ALIAS_PREFIX + keyId

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_PREFIX = "nexus.localai.model.v1."
        val KEY_ID = Regex("^[a-f0-9]{32}$")
    }
}
