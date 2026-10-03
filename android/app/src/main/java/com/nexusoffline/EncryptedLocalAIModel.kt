package com.nexusoffline

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class EncryptedLocalAIModelMetadata(
    val id: String,
    val keyId: String,
    val displayName: String,
    val sizeBytes: Long,
    val chunkCount: Int,
    val inspection: LocalAIModelInspection
)

/** Versioned authenticated container. Plaintext is never accumulated beyond one bounded chunk. */
internal object EncryptedLocalAIModel {
    private val MAGIC = byteArrayOf(0x4e, 0x58, 0x4d, 0x31) // NXM1
    private val FOOTER_MAGIC = byteArrayOf(0x4e, 0x58, 0x4d, 0x46) // NXMF
    private const val FORMAT_VERSION = 1
    const val CHUNK_BYTES = 256 * 1024
    private const val KEY_ID_BYTES = 32
    private const val NONCE_PREFIX_BYTES = 8
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    private const val FOOTER_BYTES = 8L
    private const val MAX_METADATA_BYTES = 4096
    private const val MAX_SECTIONS = 64
    private const val MAX_TEXT_BYTES = 512
    private const val HEADER_BYTES = 4 + 1 + KEY_ID_BYTES + NONCE_PREFIX_BYTES
    private val UTF8 = StandardCharsets.UTF_8

    internal data class Header(val keyId: String, val noncePrefix: ByteArray, val encoded: ByteArray)

    fun newHeader(keyId: String): Header {
        require(AndroidLocalAIModelKeyProvider.KEY_ID.matches(keyId)) { "BAD_MODEL_KEY_ID" }
        val prefix = ByteArray(NONCE_PREFIX_BYTES).also(java.security.SecureRandom()::nextBytes)
        val bytes = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(FORMAT_VERSION.toByte())
            .put(keyId.toByteArray(StandardCharsets.US_ASCII))
            .put(prefix)
            .array()
        return Header(keyId, prefix, bytes)
    }

    fun writeHeader(output: FileOutputStream, header: Header) {
        output.write(header.encoded)
        output.fd.sync()
    }

    fun encryptChunk(key: SecretKey, header: Header, chunkIndex: Int, plaintext: ByteArray, length: Int): ByteArray {
        require(chunkIndex >= 0 && chunkIndex < Int.MAX_VALUE) { "MODEL_TOO_LARGE" }
        require(length in 1..CHUNK_BYTES && length <= plaintext.size) { "BAD_MODEL_CHUNK" }
        val nonce = nonce(header.noncePrefix, chunkIndex + 1)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(chunkAad(header, chunkIndex, length))
            cipher.doFinal(plaintext, 0, length)
        } finally {
            nonce.fill(0)
        }
    }

    fun appendMetadata(
        file: File,
        key: SecretKey,
        header: Header,
        id: String,
        displayName: String,
        sizeBytes: Long,
        chunkCount: Int,
        inspection: LocalAIModelInspection
    ) {
        require(ID.matches(id)) { "BAD_MODEL_ID" }
        require(displayName == LocalAIModelStore.sanitizeModelFilename(displayName)) { "BAD_MODEL_FILENAME" }
        require(sizeBytes in 1..LocalAIModelStore.MAX_MODEL_BYTES && chunkCount > 0) { "BAD_MODEL_SIZE" }
        val clear = encodeMetadata(id, header.keyId, displayName, sizeBytes, chunkCount, inspection)
        val nonce = nonce(header.noncePrefix, 0)
        val encrypted = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(metadataAad(header, id))
            cipher.doFinal(clear)
        } finally {
            clear.fill(0)
            nonce.fill(0)
        }
        require(encrypted.size <= MAX_METADATA_BYTES) { "MODEL_METADATA_TOO_LARGE" }
        try {
            FileOutputStream(file, true).use { output ->
                output.write(encrypted)
                val tail = ByteBuffer.allocate(FOOTER_BYTES.toInt()).putInt(encrypted.size).put(FOOTER_MAGIC).array()
                output.write(tail)
                output.fd.sync()
                tail.fill(0)
            }
        } finally {
            encrypted.fill(0)
        }
    }

    fun readHeader(file: File): Header {
        require(file.isFile && file.length() >= HEADER_BYTES + TAG_BYTES + FOOTER_BYTES) { "MODEL_ENVELOPE_TRUNCATED" }
        RandomAccessFile(file, "r").use { input ->
            val bytes = ByteArray(HEADER_BYTES)
            input.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("MODEL_ENVELOPE_FORMAT")
            if (buffer.get().toInt() and 0xff != FORMAT_VERSION) throw IllegalArgumentException("MODEL_ENVELOPE_VERSION")
            val keyIdBytes = ByteArray(KEY_ID_BYTES).also(buffer::get)
            val keyId = String(keyIdBytes, StandardCharsets.US_ASCII)
            keyIdBytes.fill(0)
            if (!AndroidLocalAIModelKeyProvider.KEY_ID.matches(keyId)) throw IllegalArgumentException("MODEL_KEY_ID_INVALID")
            val prefix = ByteArray(NONCE_PREFIX_BYTES).also(buffer::get)
            magic.fill(0)
            return Header(keyId, prefix, bytes)
        }
    }

    fun readMetadata(file: File, expectedId: String, key: SecretKey): EncryptedLocalAIModelMetadata {
        require(ID.matches(expectedId)) { "BAD_MODEL_ID" }
        val header = readHeader(file)
        val encryptedMetadata: ByteArray
        RandomAccessFile(file, "r").use { input ->
            input.seek(file.length() - FOOTER_BYTES)
            val tail = ByteArray(FOOTER_BYTES.toInt())
            input.readFully(tail)
            val buffer = ByteBuffer.wrap(tail)
            val length = buffer.int
            val footer = ByteArray(FOOTER_MAGIC.size).also(buffer::get)
            tail.fill(0)
            if (!footer.contentEquals(FOOTER_MAGIC)) throw IllegalArgumentException("MODEL_METADATA_FOOTER_INVALID")
            footer.fill(0)
            if (length !in (TAG_BYTES + 1)..MAX_METADATA_BYTES) throw IllegalArgumentException("MODEL_METADATA_LENGTH_INVALID")
            val start = file.length() - FOOTER_BYTES - length
            if (start < HEADER_BYTES.toLong()) throw IllegalArgumentException("MODEL_METADATA_TRUNCATED")
            encryptedMetadata = ByteArray(length)
            input.seek(start)
            input.readFully(encryptedMetadata)
        }
        val nonce = nonce(header.noncePrefix, 0)
        val clear = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(metadataAad(header, expectedId))
            cipher.doFinal(encryptedMetadata)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("MODEL_METADATA_AUTHENTICATION_FAILED", error)
        } finally {
            nonce.fill(0)
            encryptedMetadata.fill(0)
        }
        return try {
            val metadata = decodeMetadata(clear)
            require(metadata.id == expectedId) { "MODEL_METADATA_ID_MISMATCH" }
            require(metadata.keyId == header.keyId) { "MODEL_METADATA_KEY_ID_MISMATCH" }
            val metadataLength = metadataCiphertextLength(file)
            val metadataStart = file.length() - FOOTER_BYTES - metadataLength
            validateContainerDataEnd(metadata.sizeBytes, metadata.chunkCount, metadataStart)
            require(metadataStart + metadataLength + FOOTER_BYTES == file.length()) { "MODEL_CONTAINER_LENGTH_MISMATCH" }
            metadata
        } finally {
            clear.fill(0)
            header.noncePrefix.fill(0)
            header.encoded.fill(0)
        }
    }

    /** Authenticates every chunk and content-addressed hash before exposing a staging file to JNI. */
    fun decryptTo(source: File, target: File, expectedId: String, key: SecretKey): EncryptedLocalAIModelMetadata {
        require(source.canonicalFile != target.canonicalFile) { "BAD_MODEL_STAGE_PATH" }
        val metadata = readMetadata(source, expectedId, key)
        val header = readHeader(source)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_BYTES + TAG_BYTES)
        try {
            RandomAccessFile(source, "r").use { input ->
                input.seek(HEADER_BYTES.toLong())
                FileOutputStream(target).use { output ->
                    var remaining = metadata.sizeBytes
                    repeat(metadata.chunkCount) { index ->
                        val expectedLength = minOf(CHUNK_BYTES.toLong(), remaining).toInt()
                        val length = input.readInt()
                        require(length == expectedLength) { "MODEL_CHUNK_LENGTH_INVALID" }
                        input.readFully(buffer, 0, length + TAG_BYTES)
                        val nonce = nonce(header.noncePrefix, index + 1)
                        val plaintext = try {
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
                            cipher.updateAAD(chunkAad(header, index, length))
                            cipher.doFinal(buffer, 0, length + TAG_BYTES)
                        } catch (error: AEADBadTagException) {
                            throw IllegalArgumentException("MODEL_CHUNK_AUTHENTICATION_FAILED", error)
                        } finally {
                            nonce.fill(0)
                        }
                        try {
                            require(plaintext.size == length) { "MODEL_CHUNK_LENGTH_INVALID" }
                            output.write(plaintext)
                            digest.update(plaintext)
                        } finally {
                            plaintext.fill(0)
                            buffer.fill(0)
                        }
                        remaining -= length
                    }
                    require(remaining == 0L) { "MODEL_SIZE_MISMATCH" }
                    output.fd.sync()
                }
            }
            val actualId = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualId == expectedId) { "MODEL_HASH_MISMATCH" }
            return metadata
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            buffer.fill(0)
            header.noncePrefix.fill(0)
            header.encoded.fill(0)
        }
    }

    fun noncePrefix(header: Header): ByteArray = header.noncePrefix.copyOf()

    private fun metadataCiphertextLength(file: File): Int = RandomAccessFile(file, "r").use { input ->
        input.seek(file.length() - FOOTER_BYTES)
        input.readInt()
    }

    private fun validateContainerDataEnd(size: Long, chunkCount: Int, metadataStart: Long) {
        require(size in 1..LocalAIModelStore.MAX_MODEL_BYTES) { "MODEL_SIZE_INVALID" }
        val expectedChunks = ((size - 1) / CHUNK_BYTES + 1).toInt()
        require(chunkCount == expectedChunks) { "MODEL_CHUNK_COUNT_INVALID" }
        var remaining = size
        var expected = HEADER_BYTES.toLong()
        repeat(chunkCount) {
            val clearLength = minOf(CHUNK_BYTES.toLong(), remaining)
            expected = Math.addExact(expected, 4L + clearLength + TAG_BYTES)
            remaining -= clearLength
        }
        require(expected == metadataStart) { "MODEL_CONTAINER_LAYOUT_INVALID" }
    }

    private fun encodeMetadata(
        id: String,
        keyId: String,
        displayName: String,
        sizeBytes: Long,
        chunkCount: Int,
        inspection: LocalAIModelInspection
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(id)
            output.writeUTF(keyId)
            output.writeUTF(displayName)
            output.writeLong(sizeBytes)
            output.writeInt(CHUNK_BYTES)
            output.writeInt(chunkCount)
            output.writeUTF(inspection.format.take(MAX_TEXT_BYTES))
            output.writeUTF(inspection.formatVersion.take(MAX_TEXT_BYTES))
            output.writeNullableText(inspection.modelName)
            output.writeNullableText(inspection.architecture)
            output.writeBoolean(inspection.contextLength != null)
            inspection.contextLength?.let(output::writeInt)
            output.writeNullableText(inspection.license)
            output.writeLong(inspection.estimatedRamBytes)
            require(inspection.sectionTypes.size <= MAX_SECTIONS) { "MODEL_METADATA_INVALID" }
            output.writeInt(inspection.sectionTypes.size)
            inspection.sectionTypes.forEach { output.writeUTF(it.take(128)) }
            output.writeBoolean(inspection.structurallyCompatible)
            output.writeUTF(inspection.compatibility.take(MAX_TEXT_BYTES))
        }
        return bytes.toByteArray()
    }

    private fun decodeMetadata(bytes: ByteArray): EncryptedLocalAIModelMetadata {
        require(bytes.size in 1..MAX_METADATA_BYTES) { "MODEL_METADATA_LENGTH_INVALID" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "MODEL_METADATA_VERSION" }
            val id = input.readUTF()
            val keyId = input.readUTF()
            val displayName = input.readUTF()
            val size = input.readLong()
            val chunkSize = input.readInt()
            val chunkCount = input.readInt()
            require(ID.matches(id)) { "MODEL_METADATA_ID_INVALID" }
            require(AndroidLocalAIModelKeyProvider.KEY_ID.matches(keyId)) { "MODEL_METADATA_KEY_ID_INVALID" }
            require(displayName == LocalAIModelStore.sanitizeModelFilename(displayName)) { "MODEL_METADATA_NAME_INVALID" }
            require(size in 1..LocalAIModelStore.MAX_MODEL_BYTES) { "MODEL_SIZE_INVALID" }
            require(chunkSize == CHUNK_BYTES) { "MODEL_CHUNK_SIZE_INVALID" }
            require(chunkCount == ((size - 1) / CHUNK_BYTES + 1).toInt()) { "MODEL_CHUNK_COUNT_INVALID" }
            val format = input.readUTF().take(MAX_TEXT_BYTES)
            val formatVersion = input.readUTF().take(MAX_TEXT_BYTES)
            val modelName = input.readNullableText()
            val architecture = input.readNullableText()
            val contextLength = if (input.readBoolean()) input.readInt().takeIf { it in 1..1_000_000 } ?: throw IllegalArgumentException("MODEL_METADATA_CONTEXT_INVALID") else null
            val license = input.readNullableText()
            val estimatedRam = input.readLong().also { require(it >= 0L) { "MODEL_METADATA_RAM_INVALID" } }
            val sectionCount = input.readInt()
            require(sectionCount in 0..MAX_SECTIONS) { "MODEL_METADATA_SECTIONS_INVALID" }
            val sections = List(sectionCount) { input.readUTF().take(128) }
            val structurallyCompatible = input.readBoolean()
            val compatibility = input.readUTF().take(MAX_TEXT_BYTES)
            require(input.available() == 0) { "MODEL_METADATA_TRAILING_DATA" }
            return EncryptedLocalAIModelMetadata(
                id, keyId, displayName, size, chunkCount,
                LocalAIModelInspection(
                    format, formatVersion, modelName, architecture, contextLength, license,
                    estimatedRam, sections, structurallyCompatible, compatibility
                )
            )
        }
    }

    private fun chunkAad(header: Header, index: Int, clearLength: Int): ByteArray =
        ByteBuffer.allocate(header.encoded.size + 8 + 4 + 4)
            .put(header.encoded)
            .put("chunk-v1".toByteArray(StandardCharsets.US_ASCII))
            .putInt(index)
            .putInt(clearLength)
            .array()

    private fun metadataAad(header: Header, id: String): ByteArray =
        ByteBuffer.allocate(header.encoded.size + 7 + ID_LENGTH)
            .put(header.encoded)
            .put("meta-v1".toByteArray(StandardCharsets.US_ASCII))
            .put(id.toByteArray(StandardCharsets.US_ASCII))
            .array()

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray =
        ByteBuffer.allocate(IV_BYTES).put(prefix).putInt(counter).array()

    private fun DataOutputStream.writeNullableText(value: String?) {
        writeBoolean(value != null)
        value?.let { writeUTF(it.take(MAX_TEXT_BYTES)) }
    }

    private fun DataInputStream.readNullableText(): String? = if (readBoolean()) readUTF().take(MAX_TEXT_BYTES) else null

    const val ID_LENGTH = 64
    val ID = Regex("^[a-f0-9]{64}$")
}
