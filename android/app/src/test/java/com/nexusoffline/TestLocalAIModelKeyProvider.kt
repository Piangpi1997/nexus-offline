package com.nexusoffline

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Host-only software keys; tests using this do not test Android Keystore. */
internal class TestLocalAIModelKeyProvider : LocalAIModelKeyProvider {
    private val keys = ConcurrentHashMap<String, SecretKey>()
    override fun create(keyId: String): SecretKey = keys.computeIfAbsent(keyId) {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
    override fun get(keyId: String): SecretKey? = keys[keyId]
    override fun delete(keyId: String): Boolean { keys.remove(keyId); return true }
    override fun deleteOrphaned(activeKeyIds: Set<String>): Boolean {
        keys.keys.toList().filterNot(activeKeyIds::contains).forEach(keys::remove)
        return true
    }
    override fun deleteAll(): Boolean { keys.clear(); return true }
}

private val hostTestModelKeys = TestLocalAIModelKeyProvider()

internal fun newTestLocalAIStore(root: File, availableBytes: () -> Long = { Long.MAX_VALUE }): LocalAIModelStore =
    LocalAIModelStore(
        rootDirectory = root,
        availableBytes = availableBytes,
        keyProvider = hostTestModelKeys,
        plaintextStageDirectory = File(root.parentFile ?: root, "${root.name}-plaintext-stage")
    )
