package com.nexusoffline

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Streaming authenticated-encryption format for app-private received attachments. */
internal object EncryptedAttachmentCipher {
    private val MAGIC = byteArrayOf(0x4e, 0x58, 0x41, 0x31) // NXA1
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    private const val BUFFER_BYTES = 64 * 1024
    data class Verification(val plaintextBytes: Long, val sha256: String)

    fun encrypt(source: File, target: File, transferId: String, filename: String, key: SecretKey): Long {
        validateIdentity(transferId, filename)
        require(source.isFile && NativeProtocol.acceptsFileSize(source.length())) { "BAD_SOURCE_FILE" }
        require(source.canonicalFile != target.canonicalFile) { "BAD_TARGET_FILE" }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(associatedData(transferId, filename))
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    output.write(MAGIC)
                    output.write(iv)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(output::write)
                    }
                    cipher.doFinal().let(output::write)
                    output.fd.sync()
                }
            }
            return source.length()
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            buffer.fill(0)
            iv.fill(0)
        }
    }

    /** Decrypts only to the caller's app-private temporary target; failed authentication deletes it. */
    fun decrypt(source: File, target: File, transferId: String, filename: String, key: SecretKey): Long {
        validateIdentity(transferId, filename)
        require(source.isFile && source.length() in (MAGIC.size + IV_BYTES + TAG_BYTES).toLong()..(NativeProtocol.MAX_FILE_BYTES + MAGIC.size + IV_BYTES + TAG_BYTES)) {
            "BAD_ENCRYPTED_FILE"
        }
        require(source.canonicalFile != target.canonicalFile) { "BAD_TARGET_FILE" }
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            FileInputStream(source).use { input ->
                val magic = ByteArray(MAGIC.size)
                readFully(input, magic)
                require(magic.contentEquals(MAGIC)) { "BAD_ENVELOPE" }
                val iv = ByteArray(IV_BYTES)
                readFully(input, iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(associatedData(transferId, filename))
                var plaintextBytes = 0L
                try {
                    FileOutputStream(target).use { output ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            cipher.update(buffer, 0, count)?.let { clear ->
                                plaintextBytes += clear.size
                                output.write(clear)
                                clear.fill(0)
                            }
                        }
                        cipher.doFinal().let { clear ->
                            plaintextBytes += clear.size
                            output.write(clear)
                            clear.fill(0)
                        }
                        output.fd.sync()
                    }
                } finally {
                    iv.fill(0)
                }
                require(NativeProtocol.acceptsFileSize(plaintextBytes)) { "BAD_PLAINTEXT_SIZE" }
                return plaintextBytes
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            buffer.fill(0)
        }
    }

    /** Authenticates ciphertext and hashes clear bytes in memory, without creating a plaintext file. */
    fun verify(source: File, transferId: String, filename: String, key: SecretKey): Verification {
        validateIdentity(transferId, filename)
        require(source.isFile && source.length() in (MAGIC.size + IV_BYTES + TAG_BYTES).toLong()..(NativeProtocol.MAX_FILE_BYTES + MAGIC.size + IV_BYTES + TAG_BYTES)) {
            "BAD_ENCRYPTED_FILE"
        }
        val buffer = ByteArray(BUFFER_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            FileInputStream(source).use { input ->
                val magic = ByteArray(MAGIC.size)
                readFully(input, magic)
                require(magic.contentEquals(MAGIC)) { "BAD_ENVELOPE" }
                val iv = ByteArray(IV_BYTES)
                readFully(input, iv)
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                    cipher.updateAAD(associatedData(transferId, filename))
                    var plaintextBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let { clear ->
                            plaintextBytes += clear.size
                            digest.update(clear)
                            clear.fill(0)
                        }
                    }
                    cipher.doFinal().let { clear ->
                        plaintextBytes += clear.size
                        digest.update(clear)
                        clear.fill(0)
                    }
                    require(NativeProtocol.acceptsFileSize(plaintextBytes)) { "BAD_PLAINTEXT_SIZE" }
                    return Verification(plaintextBytes, digest.digest().joinToString("") { "%02x".format(it) })
                } finally {
                    iv.fill(0)
                }
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun validateIdentity(transferId: String, filename: String) {
        require(NativeProtocol.isTransferId(transferId)) { "BAD_TRANSFER_ID" }
        require(NativeProtocol.sanitizeFilename(filename) == filename) { "BAD_FILENAME" }
    }

    private fun associatedData(transferId: String, filename: String): ByteArray {
        val id = transferId.toByteArray(StandardCharsets.US_ASCII)
        val name = filename.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(MAGIC.size + id.size + 2 + name.size)
            .put(MAGIC)
            .put(id)
            .putShort(name.size.toShort())
            .put(name)
            .array()
    }

    private fun readFully(input: InputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = input.read(destination, offset, destination.size - offset)
            if (count < 0) throw IOException("TRUNCATED_ENVELOPE")
            offset += count
        }
    }
}
