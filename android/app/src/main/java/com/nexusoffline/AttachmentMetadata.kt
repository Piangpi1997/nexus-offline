package com.nexusoffline

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

internal data class StoredAttachmentMetadata(
    val transferId: String,
    val filename: String,
    val sizeBytes: Long,
    val receivedAtMillis: Long,
    val sha256: String
)

internal object AttachmentMetadataCodec {
    private val MAGIC = byteArrayOf(0x4e, 0x41, 0x4d, 0x31) // NAM1
    private const val MAX_METADATA_BYTES = 4096

    fun encode(metadata: StoredAttachmentMetadata): ByteArray {
        validate(metadata)
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC)
                output.writeUTF(metadata.transferId)
                output.writeUTF(metadata.filename)
                output.writeLong(metadata.sizeBytes)
                output.writeLong(metadata.receivedAtMillis)
                output.writeUTF(metadata.sha256.lowercase())
            }
        }.toByteArray()
        require(bytes.size <= MAX_METADATA_BYTES) { "METADATA_TOO_LARGE" }
        return bytes
    }

    fun decode(bytes: ByteArray): StoredAttachmentMetadata {
        require(bytes.size in (MAGIC.size + 2)..MAX_METADATA_BYTES) { "BAD_METADATA_SIZE" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(MessageDigest.isEqual(magic, MAGIC)) { "BAD_METADATA_VERSION" }
        val result = StoredAttachmentMetadata(
            transferId = input.readUTF(),
            filename = input.readUTF(),
            sizeBytes = input.readLong(),
            receivedAtMillis = input.readLong(),
            sha256 = input.readUTF()
        )
        require(input.available() == 0) { "TRAILING_METADATA" }
        validate(result)
        return result.copy(sha256 = result.sha256.lowercase())
    }

    private fun validate(metadata: StoredAttachmentMetadata) {
        require(NativeProtocol.isTransferId(metadata.transferId)) { "BAD_TRANSFER_ID" }
        require(NativeProtocol.sanitizeFilename(metadata.filename) == metadata.filename) { "BAD_FILENAME" }
        require(NativeProtocol.acceptsFileSize(metadata.sizeBytes)) { "BAD_FILE_SIZE" }
        require(metadata.receivedAtMillis > 0L) { "BAD_RECEIVED_TIME" }
        require(metadata.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "BAD_SHA256" }
    }
}

internal object LegacyAttachmentMigration {
    data class Result(val migrated: Int, val remainingPlaintext: Int)

    fun migrate(
        files: List<File>,
        alreadyStored: (String) -> Boolean,
        commitEncrypted: (File, String, String) -> Unit
    ): Result {
        var migrated = 0
        var remaining = 0
        val pattern = Regex("^([a-fA-F0-9]{32})_(.+)$")
        for (source in files) {
            val match = pattern.matchEntire(source.name)
            val id = match?.groupValues?.getOrNull(1)
            val name = match?.groupValues?.getOrNull(2)
            if (!source.isFile || id == null || name == null || NativeProtocol.sanitizeFilename(name) != name || alreadyStored(id)) {
                remaining++
                continue
            }
            try {
                commitEncrypted(source, id, name)
                if (source.delete()) migrated++ else remaining++
            } catch (_: Exception) {
                // Preserve the only plaintext source when encrypted commit fails.
                remaining++
            }
        }
        return Result(migrated, remaining)
    }
}

internal object AttachmentTempFiles {
    fun cleanupExpired(directory: File, nowMillis: Long, maxAgeMillis: Long): Int {
        if (!directory.isDirectory || maxAgeMillis < 0L) return 0
        var removed = 0
        directory.listFiles()?.filter(File::isFile)?.forEach { file ->
            val age = nowMillis - file.lastModified()
            if (age >= maxAgeMillis && file.delete()) removed++
        }
        return removed
    }

    fun safeOutput(directory: File, transferId: String, filename: String): File {
        require(NativeProtocol.isTransferId(transferId)) { "BAD_TRANSFER_ID" }
        require(NativeProtocol.sanitizeFilename(filename) == filename) { "BAD_FILENAME" }
        val root = directory.canonicalFile
        val output = File(root, "$transferId-$filename").canonicalFile
        require(output.parentFile == root) { "BAD_TARGET_PATH" }
        return output
    }
}
