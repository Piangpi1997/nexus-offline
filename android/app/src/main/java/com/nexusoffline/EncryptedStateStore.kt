package com.nexusoffline

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted-at-rest JSON app state. The encryption key never leaves Android Keystore. */
internal class EncryptedStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val atomicFile = AtomicFile(File(appContext.filesDir, STATE_FILE))

    @Synchronized
    fun load(): JSONObject {
        if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) {
            return JSONObject().put("ok", true).put("found", false)
        }
        return try {
            val envelope = atomicFile.openRead().use { input ->
                require(input.available() in (MAGIC.size + IV_BYTES + TAG_BYTES)..MAX_ENVELOPE_BYTES) { "bad envelope size" }
                val bytes = input.readBytes()
                bytes
            }
            require(envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "bad envelope version" }
            val iv = envelope.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
            val encrypted = envelope.copyOfRange(MAGIC.size + IV_BYTES, envelope.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey() ?: error("missing key"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(MAGIC)
            val clear = cipher.doFinal(encrypted)
            val raw = String(clear, StandardCharsets.UTF_8)
            JSONObject(raw) // Reject malformed plaintext before returning it to the WebView.
            JSONObject().put("ok", true).put("found", true).put("data", raw)
        } catch (_: AEADBadTagException) {
            JSONObject().put("ok", false).put("error", "DECRYPT_FAILED")
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("error", "SECURE_STORAGE_UNAVAILABLE")
        }
    }

    @Synchronized
    fun save(raw: String): JSONObject {
        if (raw.toByteArray(StandardCharsets.UTF_8).size !in 2..MAX_PLAINTEXT_BYTES) {
            return JSONObject().put("ok", false).put("error", "STATE_SIZE_INVALID")
        }
        return try {
            JSONObject(raw)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(MAGIC)
            val encrypted = cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8))
            val envelope = MAGIC + cipher.iv + encrypted
            val stream = atomicFile.startWrite()
            try {
                stream.write(envelope)
                stream.fd.sync()
                atomicFile.finishWrite(stream)
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                throw error
            }
            JSONObject().put("ok", true)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("error", "SECURE_STORAGE_WRITE_FAILED")
        }
    }

    @Synchronized
    fun clear(): JSONObject {
        return try {
            atomicFile.delete()
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            JSONObject().put("ok", true)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("error", "SECURE_STORAGE_CLEAR_FAILED")
        }
    }

    @Synchronized
    fun storedBytes(): Long = if (atomicFile.baseFile.exists()) atomicFile.baseFile.length() else 0L

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
    }

    private fun getOrCreateKey(): SecretKey = getExistingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }
        .generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nexus.offline.state.aes.v1"
        const val STATE_FILE = "nexus-state-v1.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = TAG_BYTES * 8
        const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024
        const val MAX_ENVELOPE_BYTES = MAX_PLAINTEXT_BYTES + 128
        val MAGIC = byteArrayOf(0x4e, 0x58, 0x53, 0x31) // NXS1
    }
}
