package com.nexusoffline

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKey

internal data class ImportedLocalAIModel(
    val id: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val inspection: LocalAIModelInspection?,
    val inspectionError: String? = null
)

internal data class LegacyLocalAIModelMigrationResult(val migrated: Int, val remainingPlaintextFiles: Int)

/** Stores authenticated encrypted model containers; plaintext exists only in bounded, private temporary staging. */
internal class LocalAIModelStore(
    private val rootDirectory: File,
    private val availableBytes: () -> Long,
    private val keyProvider: LocalAIModelKeyProvider,
    private val plaintextStageDirectory: File,
    private val inspector: LocalAIModelInspector = LocalAIModelInspector()
) {
    @Synchronized
    fun importModel(displayName: String, source: InputStream, expectedSizeBytes: Long? = null): ImportedLocalAIModel {
        val safeName = sanitizeModelFilename(displayName)
        require(safeName.endsWith(".litertlm", ignoreCase = true)) { "UNSUPPORTED_MODEL_FORMAT" }
        if (expectedSizeBytes != null) {
            require(expectedSizeBytes >= 0L) { "BAD_MODEL_SIZE" }
            require(expectedSizeBytes <= MAX_MODEL_BYTES) { "MODEL_TOO_LARGE" }
        }
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) throw IllegalStateException("MODEL_STORAGE_UNAVAILABLE")
        val root = rootDirectory.canonicalFile
        require(root.isDirectory) { "MODEL_STORAGE_UNAVAILABLE" }
        if (!plaintextStageDirectory.exists() && !plaintextStageDirectory.mkdirs()) throw IllegalStateException("MODEL_STAGING_UNAVAILABLE")
        val stageRoot = plaintextStageDirectory.canonicalFile
        require(stageRoot.isDirectory && stageRoot != root) { "MODEL_STAGING_UNAVAILABLE" }
        if (expectedSizeBytes != null && availableBytes() < minimumImportWorkspace(expectedSizeBytes)) {
            throw IllegalStateException("INSUFFICIENT_MODEL_STORAGE")
        }
        val token = UUID.randomUUID().toString().replace("-", "")
        val keyId = token
        val part = File(root, "import-$token.nxm.part").canonicalFile
        val plaintextStage = File(stageRoot, "import-$token.plain.part").canonicalFile
        require(part.parentFile == root && plaintextStage.parentFile == stageRoot) { "BAD_MODEL_PATH" }
        require(part.createNewFile()) { "MODEL_IMPORT_CREATE_FAILED" }
        setPrivateFile(part)
        var keyCreated = false
        var committedTarget: File? = null
        var total = 0L
        var chunkCount = 0
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(EncryptedLocalAIModel.CHUNK_BYTES)
        var header: EncryptedLocalAIModel.Header? = null
        try {
            val newHeader = EncryptedLocalAIModel.newHeader(keyId)
            header = newHeader
            FileOutputStream(part).use { encryptedOutput ->
                EncryptedLocalAIModel.writeHeader(encryptedOutput, newHeader)
                val modelKey = keyProvider.create(keyId)
                keyCreated = true
                FileOutputStream(plaintextStage).use { plainOutput ->
                    setPrivateFile(plaintextStage)
                    source.use { input ->
                        while (true) {
                            val count = readChunk(input, buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_MODEL_BYTES) { "MODEL_TOO_LARGE" }
                            if (expectedSizeBytes != null && total > expectedSizeBytes) throw IllegalArgumentException("MODEL_SIZE_MISMATCH")
                            val ciphertextBytes = count + 4 + 16
                            if (availableBytes() < count.toLong() + ciphertextBytes) throw IllegalStateException("INSUFFICIENT_MODEL_STORAGE")
                            digest.update(buffer, 0, count)
                            plainOutput.write(buffer, 0, count)
                            val encrypted = EncryptedLocalAIModel.encryptChunk(modelKey, newHeader, chunkCount, buffer, count)
                            try {
                                val length = java.nio.ByteBuffer.allocate(4).putInt(count).array()
                                encryptedOutput.write(length)
                                encryptedOutput.write(encrypted)
                                length.fill(0)
                            } finally {
                                encrypted.fill(0)
                            }
                            chunkCount++
                            buffer.fill(0)
                        }
                        plainOutput.fd.sync()
                    }
                }
                require(total > 0L) { "EMPTY_MODEL" }
                if (expectedSizeBytes != null && total != expectedSizeBytes) throw IllegalArgumentException("MODEL_SIZE_MISMATCH")
                val id = digest.digest().joinToString("") { "%02x".format(it) }
                val inspection = inspector.inspect(plaintextStage, id)
                EncryptedLocalAIModel.appendMetadata(part, modelKey, newHeader, id, safeName, total, chunkCount, inspection)
                encryptedOutput.fd.sync()
                val target = File(root, "$id.nxm").canonicalFile
                require(target.parentFile == root) { "BAD_MODEL_PATH" }
                if (target.exists()) {
                    val existing = readStoredModel(target, id)
                    require(existing.inspection != null) { "EXISTING_MODEL_INVALID" }
                    part.delete()
                    keyProvider.delete(keyId)
                    return existing
                }
                if (!part.renameTo(target)) throw IllegalStateException("MODEL_COMMIT_FAILED")
                committedTarget = target
                return ImportedLocalAIModel(id, safeName, target, total, inspection)
            }
        } catch (error: Throwable) {
            part.delete()
            committedTarget?.delete()
            if (keyCreated) keyProvider.delete(keyId)
            throw error
        } finally {
            buffer.fill(0)
            plaintextStage.delete()
            header?.noncePrefix?.fill(0)
            header?.encoded?.fill(0)
        }
    }

    /** SAF picker cancellation is represented as null and leaves no model state. */
    @Synchronized
    fun importPickedModel(displayName: String?, source: InputStream?, expectedSizeBytes: Long? = null): ImportedLocalAIModel? {
        if (displayName == null || source == null) {
            runCatching { source?.close() }
            return null
        }
        return try {
            importModel(displayName, source, expectedSizeBytes)
        } catch (error: Exception) {
            runCatching { source.close() }
            throw error
        }
    }

    @Synchronized
    fun hasLegacyPlaintextModels(): Boolean {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return false
        return root.isDirectory && root.listFiles()?.any { file ->
            file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.litertlm$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        } == true
    }

    /** Re-encrypts old plaintext model files in bounded chunks and deletes source only after commit. */
    @Synchronized
    fun migrateLegacyPlaintextModels(): LegacyLocalAIModelMigrationResult {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return LegacyLocalAIModelMigrationResult(0, 0)
        if (!root.isDirectory) return LegacyLocalAIModelMigrationResult(0, 0)
        val legacyFiles = root.listFiles()?.filter { file ->
            file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.litertlm$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        }.orEmpty()
        var migrated = 0
        for (legacy in legacyFiles) {
            val id = legacy.name.removeSuffix(".litertlm")
            try {
                if (sha256(legacy) != id) continue
                val label = readLegacyDisplayName(root, id)
                val encrypted = FileInputStream(legacy).use { input -> importModel(label, input, legacy.length()) }
                if (encrypted.id != id) continue
                if (!legacy.delete()) continue
                val oldLabel = File(root, "$id.name").canonicalFile
                if (oldLabel.parentFile == root) oldLabel.delete()
                migrated++
            } catch (_: Exception) {
                // Preserve the original if encryption or integrity validation fails.
            }
        }
        val remainingWeights = root.listFiles()?.count { file ->
            file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.litertlm$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        } ?: 0
        val remainingLabels = root.listFiles()?.count { file ->
            file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.name$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        } ?: 0
        val remaining = remainingWeights + remainingLabels
        return LegacyLocalAIModelMigrationResult(migrated, remaining)
    }

    @Synchronized
    fun listModels(): List<ImportedLocalAIModel> {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        return root.listFiles()?.asSequence()
            ?.filter { file -> file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.nxm$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false) }
            ?.map { file -> readStoredModel(file, file.name.removeSuffix(".nxm")) }
            ?.sortedBy { it.displayName.lowercase() }
            ?.toList() ?: emptyList()
    }

    @Synchronized
    fun findModel(id: String): ImportedLocalAIModel? {
        if (!EncryptedLocalAIModel.ID.matches(id)) return null
        return listModels().firstOrNull { it.id == id }
    }

    /** Decrypts authenticated chunks to a unique app-private staging file, hashes, then reinspects before returning it. */
    @Synchronized
    fun stageModelForRuntime(id: String): File {
        require(EncryptedLocalAIModel.ID.matches(id)) { "BAD_MODEL_ID" }
        val model = findModel(id) ?: throw IllegalStateException("MODEL_NOT_INSTALLED")
        val metadata = model.inspection?.let { model } ?: throw IllegalStateException(model.inspectionError ?: "MODEL_METADATA_INVALID")
        if (availableBytes() < metadata.sizeBytes) throw IllegalStateException("INSUFFICIENT_MODEL_STORAGE")
        if (!plaintextStageDirectory.exists() && !plaintextStageDirectory.mkdirs()) throw IllegalStateException("MODEL_STAGING_UNAVAILABLE")
        val stageRoot = plaintextStageDirectory.canonicalFile
        val target = File(stageRoot, "$id-${UUID.randomUUID().toString().replace("-", "")}.litertlm").canonicalFile
        val part = File(stageRoot, target.name + ".part").canonicalFile
        require(target.parentFile == stageRoot && part.parentFile == stageRoot && stageRoot != rootDirectory.canonicalFile) { "BAD_MODEL_STAGE_PATH" }
        require(part.createNewFile()) { "MODEL_STAGE_CREATE_FAILED" }
        setPrivateFile(part)
        try {
            val header = EncryptedLocalAIModel.readHeader(model.file)
            val key = existingModelKey(header.keyId)
            val decoded = EncryptedLocalAIModel.decryptTo(model.file, part, id, key)
            require(decoded.sizeBytes == model.sizeBytes && decoded.inspection.structurallyCompatible) { "MODEL_METADATA_INVALID" }
            val inspected = inspector.inspect(part, id)
            require(inspected.structurallyCompatible) { "UNSUPPORTED_MODEL_CONTENT" }
            if (!part.renameTo(target)) throw IllegalStateException("MODEL_STAGE_COMMIT_FAILED")
            setPrivateFile(target)
            return target
        } catch (error: Throwable) {
            part.delete()
            target.delete()
            throw error
        }
    }

    @Synchronized
    fun cleanupRuntimeStage(stage: File?): Boolean {
        if (stage == null) return true
        val stageRoot = runCatching { plaintextStageDirectory.canonicalFile }.getOrNull() ?: return false
        val file = runCatching { stage.canonicalFile }.getOrNull() ?: return false
        if (file.parentFile != stageRoot) return false
        return !file.exists() || file.delete()
    }

    /** Called after runtime close on memory pressure; removes all app-private plaintext staging. */
    @Synchronized
    fun cleanupPlaintextStageCache(): Int {
        val stageRoot = runCatching { plaintextStageDirectory.canonicalFile }.getOrNull() ?: return 0
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return 0
        if (stageRoot == root || !stageRoot.isDirectory) return 0
        val removed = stageRoot.listFiles()?.count { child -> runCatching { child.deleteRecursively() }.getOrDefault(false) } ?: 0
        if (stageRoot.listFiles().isNullOrEmpty()) stageRoot.delete()
        return removed
    }

    @Synchronized
    fun deleteModel(id: String): Boolean {
        if (!EncryptedLocalAIModel.ID.matches(id)) return false
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return false
        if (!root.isDirectory) return true
        val model = File(root, "$id.nxm").canonicalFile
        if (model.parentFile != root) return false
        if (!model.exists()) return true
        val keyId = runCatching { EncryptedLocalAIModel.readHeader(model).keyId }.getOrNull() ?: return false
        val removed = model.isFile && model.delete()
        return removed && keyProvider.delete(keyId)
    }

    /** Removes interrupted encrypted imports, orphaned display files, stale plaintext staging and unreferenced model keys. */
    @Synchronized
    fun cleanupIncompleteImports(): Int {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return 0
        var removed = 0
        if (root.isDirectory) {
            root.listFiles()?.filter { file -> file.isFile && file.name.matches(Regex("^import-[a-f0-9]{32}\\.nxm\\.part$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false) }?.forEach { file ->
                val keyId = runCatching { EncryptedLocalAIModel.readHeader(file).keyId }.getOrNull()
                if (file.delete()) removed++
                if (keyId != null) keyProvider.delete(keyId)
            }
            root.listFiles()?.filter { file -> file.isFile && file.name.endsWith(".name.part") }?.forEach { if (it.delete()) removed++ }
        }
        if (plaintextStageDirectory.exists()) {
            val stageRoot = runCatching { plaintextStageDirectory.canonicalFile }.getOrNull()
            if (stageRoot != null && stageRoot != root && stageRoot.isDirectory) {
                removed += stageRoot.listFiles()?.count { child -> child.deleteRecursively() } ?: 0
                if (stageRoot.listFiles().isNullOrEmpty()) stageRoot.delete()
            }
        }
        val liveKeyIds = root.listFiles()?.filter { it.isFile && it.name.matches(Regex("^[a-f0-9]{64}\\.nxm$")) }
            ?.mapNotNull { runCatching { EncryptedLocalAIModel.readHeader(it).keyId }.getOrNull() }?.toSet().orEmpty()
        keyProvider.deleteOrphaned(liveKeyIds)
        return removed
    }

    @Synchronized
    fun clear(): Boolean {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return false
        var removed = true
        if (root.exists()) {
            if (!root.isDirectory || root == plaintextStageDirectory.canonicalFile) return false
            root.listFiles()?.forEach { child ->
                val ok = runCatching { child.canonicalFile.parentFile == root && child.delete() }.getOrDefault(false)
                removed = removed && ok
            }
            if (root.exists()) removed = root.delete() && removed
        }
        cleanupRuntimeStageDirectory()
        return keyProvider.deleteAll() && removed
    }

    fun encryptedBytes(): Long = rootDirectory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    private fun readStoredModel(file: File, id: String): ImportedLocalAIModel {
        val fallback = ImportedLocalAIModel(id, "Imported model ${id.take(12)}", file, file.length(), null, "MODEL_METADATA_UNAVAILABLE")
        return runCatching {
            val header = EncryptedLocalAIModel.readHeader(file)
            val key = existingModelKey(header.keyId)
            val metadata = EncryptedLocalAIModel.readMetadata(file, id, key)
            ImportedLocalAIModel(id, metadata.displayName, file, metadata.sizeBytes, metadata.inspection)
        }.getOrElse { error ->
            val code = error.message?.takeIf { it.matches(Regex("^[A-Z0-9_]{2,64}$")) } ?: "MODEL_METADATA_CORRUPT"
            fallback.copy(inspectionError = code)
        }
    }

    private fun existingModelKey(keyId: String): SecretKey = try {
        keyProvider.get(keyId) ?: throw IllegalStateException("MODEL_KEY_MISSING")
    } catch (error: IllegalStateException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException("MODEL_KEY_UNAVAILABLE", error)
    }

    private fun readLegacyDisplayName(root: File, id: String): String {
        val label = File(root, "$id.name").canonicalFile
        if (label.parentFile == root && label.isFile && label.length() in 1..256) {
            val candidate = runCatching { label.readText(Charsets.UTF_8) }.getOrNull()
            if (candidate != null) {
                val safe = runCatching { sanitizeModelFilename(candidate) }.getOrNull()
                if (safe != null && safe.endsWith(".litertlm", ignoreCase = true)) return safe
            }
        }
        return "Imported-${id.take(12)}.litertlm"
    }

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

    private fun cleanupRuntimeStageDirectory() {
        val root = runCatching { plaintextStageDirectory.canonicalFile }.getOrNull() ?: return
        if (root == rootDirectory.canonicalFile) return
        root.deleteRecursively()
    }

    private fun setPrivateFile(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    private fun minimumImportWorkspace(size: Long): Long {
        val chunks = (size - 1) / EncryptedLocalAIModel.CHUNK_BYTES + 1
        return runCatching {
            Math.addExact(Math.multiplyExact(size, 2L), Math.addExact(chunks * 20L, 512L))
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) return if (count == 0) -1 else count
            if (read == 0) {
                val one = input.read()
                if (one < 0) return if (count == 0) -1 else count
                buffer[count++] = one.toByte()
            } else count += read
        }
        return count
    }

    companion object {
        const val MAX_MODEL_BYTES = 8L * 1024L * 1024L * 1024L
        internal fun sanitizeModelFilename(value: String): String {
            val leaf = value.replace('\\', '/').substringAfterLast('/').trim()
            require(leaf.isNotEmpty() && leaf.length <= 180 && !leaf.any { it.code < 0x20 || it == '\u007f' }) { "BAD_MODEL_FILENAME" }
            require(leaf.none { it in "<>:\"|?*" }) { "BAD_MODEL_FILENAME" }
            return leaf
        }
    }
}
