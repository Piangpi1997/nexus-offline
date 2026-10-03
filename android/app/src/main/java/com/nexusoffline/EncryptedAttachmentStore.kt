package com.nexusoffline

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** App-private attachment files and their metadata are encrypted with a non-exportable Android Keystore AES key. */
internal class EncryptedAttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY_NAME)
    private val metadataStaging = File(appContext.cacheDir, METADATA_STAGING_DIRECTORY)
    private val keyAlias = "nexus.offline.attachments.aes.v1"

    data class MigrationResult(val migrated: Int, val remainingPlaintext: Int)
    data class AttachmentRecord(
        val transferId: String,
        val filename: String,
        val sizeBytes: Long,
        val receivedAtMillis: Long,
        val sha256: String?,
        val integrityStatus: String
    )

    @Synchronized
    fun contains(transferId: String): Boolean {
        if (!NativeProtocol.isTransferId(transferId)) return false
        return File(directory, "$transferId.nxa").isFile
    }

    @Synchronized
    fun delete(transferId: String): Boolean {
        if (!NativeProtocol.isTransferId(transferId)) return false
        val stored = File(directory, "$transferId.nxa")
        val metadata = metadataFile(transferId)
        val dataRemoved = !stored.exists() || stored.delete()
        val metadataRemoved = !metadata.exists() || metadata.delete()
        return dataRemoved && metadataRemoved
    }

    @Synchronized
    fun cleanupIncompleteWrites() {
        directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("^[A-Fa-f0-9]{32}\\.nxa(?:\\.meta)?\\.part$")) }
            ?.forEach { it.delete() }
        metadataStaging.deleteRecursively()
    }

    @Synchronized
    fun storeVerifiedFile(source: File, transferId: String, filename: String): File {
        require(NativeProtocol.isTransferId(transferId)) { "BAD_TRANSFER_ID" }
        require(NativeProtocol.sanitizeFilename(filename) == filename) { "BAD_FILENAME" }
        require(source.isFile && NativeProtocol.acceptsFileSize(source.length())) { "BAD_SOURCE_FILE" }
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("ATTACHMENT_STORAGE_UNAVAILABLE")
        val target = File(directory, "$transferId.nxa")
        val metadataTarget = metadataFile(transferId)
        if (target.exists() || metadataTarget.exists()) throw IllegalStateException("DUPLICATE_FILE")
        val encryptedPart = File(directory, "$transferId.nxa.part")
        val metadataPart = File(directory, "$transferId.nxa.meta.part")
        encryptedPart.delete()
        metadataPart.delete()
        try {
            val key = getOrCreateKey()
            val size = EncryptedAttachmentCipher.encrypt(source, encryptedPart, transferId, filename, key)
            val metadata = StoredAttachmentMetadata(
                transferId = transferId,
                filename = filename,
                sizeBytes = size,
                receivedAtMillis = System.currentTimeMillis(),
                sha256 = sha256(source)
            )
            if (!metadataStaging.exists() && !metadataStaging.mkdirs()) throw IllegalStateException("METADATA_STAGING_UNAVAILABLE")
            val clearMetadata = AttachmentMetadataCodec.encode(metadata)
            val metadataClearFile = File(metadataStaging, "$transferId-${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(metadataClearFile).use { output ->
                    output.write(clearMetadata)
                    output.fd.sync()
                }
                EncryptedAttachmentCipher.encrypt(metadataClearFile, metadataPart, transferId, METADATA_AAD_FILENAME, key)
            } finally {
                clearMetadata.fill(0)
                metadataClearFile.delete()
            }
            if (!encryptedPart.renameTo(target)) throw IllegalStateException("ATTACHMENT_COMMIT_FAILED")
            if (!metadataPart.renameTo(metadataTarget)) {
                target.delete()
                throw IllegalStateException("ATTACHMENT_METADATA_COMMIT_FAILED")
            }
            return target
        } catch (error: Exception) {
            encryptedPart.delete()
            metadataPart.delete()
            throw error
        }
    }

    @Synchronized
    fun metadataForUserAction(transferId: String): AttachmentRecord {
        require(NativeProtocol.isTransferId(transferId)) { "BAD_TRANSFER_ID" }
        require(File(directory, "$transferId.nxa").isFile) { "ATTACHMENT_NOT_FOUND" }
        val key = getExistingKey() ?: throw IllegalStateException("ATTACHMENT_KEY_UNAVAILABLE")
        val metadata = readMetadata(transferId, key)
        require(metadata.transferId == transferId) { "ATTACHMENT_METADATA_INVALID" }
        return AttachmentRecord(transferId, metadata.filename, metadata.sizeBytes, metadata.receivedAtMillis, metadata.sha256, "pending-authentication")
    }

    /** Lists only safe identifiers; metadata and ciphertext are authenticated before a record is marked verified. */
    @Synchronized
    fun listAttachments(): List<AttachmentRecord> {
        val files = directory.listFiles()?.filter { it.isFile && it.name.matches(Regex("^[A-Fa-f0-9]{32}\\.nxa$")) }.orEmpty()
        if (files.isEmpty()) return emptyList()
        val key = runCatching { getExistingKey() }.getOrNull()
        return files.map { dataFile ->
            val id = dataFile.name.removeSuffix(".nxa")
            val fallback = AttachmentRecord(id, "Metadata unavailable", 0L, dataFile.lastModified(), null, "metadata-unavailable")
            if (key == null) return@map fallback.copy(integrityStatus = "key-unavailable")
            val metadata = runCatching { readMetadata(id, key) }.getOrNull() ?: return@map fallback
            if (metadata.transferId != id) return@map fallback.copy(integrityStatus = "metadata-invalid")
            val checked = runCatching { EncryptedAttachmentCipher.verify(dataFile, id, metadata.filename, key) }.getOrNull()
            val valid = checked != null && checked.plaintextBytes == metadata.sizeBytes &&
                MessageDigest.isEqual(checked.sha256.toByteArray(), metadata.sha256.toByteArray())
            AttachmentRecord(
                transferId = id,
                filename = metadata.filename,
                sizeBytes = metadata.sizeBytes,
                receivedAtMillis = metadata.receivedAtMillis,
                sha256 = metadata.sha256,
                integrityStatus = if (valid) "verified" else "integrity-failed"
            )
        }.sortedByDescending { it.receivedAtMillis }
    }

    /** Returns a controlled cache file only after metadata, GCM tag, byte count, and SHA-256 all match. */
    @Synchronized
    fun preparePlaintextForUserAction(transferId: String, destination: File): AttachmentRecord {
        require(NativeProtocol.isTransferId(transferId)) { "BAD_TRANSFER_ID" }
        val source = File(directory, "$transferId.nxa")
        require(source.isFile) { "ATTACHMENT_NOT_FOUND" }
        val key = getExistingKey() ?: throw IllegalStateException("ATTACHMENT_KEY_UNAVAILABLE")
        val metadata = readMetadata(transferId, key)
        require(metadata.transferId == transferId) { "ATTACHMENT_METADATA_INVALID" }
        require(NativeProtocol.sanitizeFilename(metadata.filename) == metadata.filename) { "BAD_FILENAME" }
        require(destination.canonicalFile.parentFile == destination.parentFile?.canonicalFile) { "BAD_TARGET_PATH" }
        require(!destination.exists()) { "PLAINTEXT_TEMP_ALREADY_EXISTS" }
        try {
            val written = EncryptedAttachmentCipher.decrypt(source, destination, transferId, metadata.filename, key)
            val actualHash = sha256(destination)
            require(written == metadata.sizeBytes && MessageDigest.isEqual(actualHash.toByteArray(), metadata.sha256.toByteArray())) {
                "ATTACHMENT_INTEGRITY_FAILED"
            }
            return AttachmentRecord(transferId, metadata.filename, metadata.sizeBytes, metadata.receivedAtMillis, metadata.sha256, "verified")
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    /** Converts legacy files and removes each source only after encrypted data plus encrypted metadata commit. */
    @Synchronized
    fun migrateLegacyPlaintextFiles(): MigrationResult {
        val legacyDirectory = File(appContext.filesDir, LEGACY_DIRECTORY_NAME)
        val files = legacyDirectory.listFiles()?.toList().orEmpty()
        val result = LegacyAttachmentMigration.migrate(
            files = files,
            alreadyStored = ::contains,
            commitEncrypted = { source, transferId, filename -> storeVerifiedFile(source, transferId, filename) }
        )
        if (legacyDirectory.listFiles()?.isEmpty() == true) legacyDirectory.delete()
        return MigrationResult(result.migrated, result.remainingPlaintext)
    }

    @Synchronized
    fun clear(): Boolean {
        val legacyDirectory = File(appContext.filesDir, LEGACY_DIRECTORY_NAME)
        val shareDirectory = File(appContext.cacheDir, SHARE_DIRECTORY)
        if (!AttachmentFileCleanup.clearDirectories(directory, legacyDirectory, metadataStaging, shareDirectory)) return false
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareDirectory(): File = File(appContext.cacheDir, SHARE_DIRECTORY)
    fun attachmentCount(): Int = directory.listFiles()?.count { it.isFile && it.name.matches(Regex("^[A-Fa-f0-9]{32}\\.nxa$")) } ?: 0
    fun encryptedBytes(): Long = directory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
    fun migrationPendingCount(): Int = File(appContext.filesDir, LEGACY_DIRECTORY_NAME).listFiles()?.count(File::isFile) ?: 0

    private fun readMetadata(transferId: String, key: SecretKey): StoredAttachmentMetadata {
        val encryptedMetadata = metadataFile(transferId)
        require(encryptedMetadata.isFile) { "ATTACHMENT_METADATA_MISSING" }
        if (!metadataStaging.exists() && !metadataStaging.mkdirs()) throw IllegalStateException("METADATA_STAGING_UNAVAILABLE")
        val clearFile = File(metadataStaging, "$transferId-${UUID.randomUUID()}.read.tmp")
        try {
            EncryptedAttachmentCipher.decrypt(encryptedMetadata, clearFile, transferId, METADATA_AAD_FILENAME, key)
            require(clearFile.length() <= 4096L) { "BAD_METADATA_SIZE" }
            val bytes = clearFile.readBytes()
            return try { AttachmentMetadataCodec.decode(bytes) } finally { bytes.fill(0) }
        } finally {
            clearFile.delete()
        }
    }

    private fun metadataFile(transferId: String) = File(directory, "$transferId.nxa.meta")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        try {
            FileInputStream(file).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            buffer.fill(0)
        }
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey = getExistingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
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
        const val DIRECTORY_NAME = "received-attachments-encrypted"
        const val LEGACY_DIRECTORY_NAME = "received-files"
        const val METADATA_STAGING_DIRECTORY = "nexus-attachment-metadata-staging"
        const val SHARE_DIRECTORY = "nexus-attachment-share"
        const val METADATA_AAD_FILENAME = "metadata.nam"
    }
}
