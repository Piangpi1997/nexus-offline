package com.nexusoffline

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

internal data class ImportedLocalAIModel(
    val id: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val inspection: LocalAIModelInspection?,
    val inspectionError: String? = null
)

/** Stores user-selected model weights privately; it never downloads or fabricates models. */
internal class LocalAIModelStore(
    private val rootDirectory: File,
    private val inspector: LocalAIModelInspector = LocalAIModelInspector(),
    private val availableBytes: () -> Long
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
        if (expectedSizeBytes != null && availableBytes() < expectedSizeBytes) {
            throw IllegalStateException("INSUFFICIENT_MODEL_STORAGE")
        }
        val part = File(root, "import-${UUID.randomUUID()}.part").canonicalFile
        require(part.parentFile == root) { "BAD_MODEL_PATH" }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var newlyInstalledTarget: File? = null
        try {
            source.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        var count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            val oneByte = input.read()
                            if (oneByte < 0) break
                            buffer[0] = oneByte.toByte()
                            count = 1
                        }
                        total += count
                        require(total <= MAX_MODEL_BYTES) { "MODEL_TOO_LARGE" }
                        if (availableBytes() < count.toLong()) throw IllegalStateException("INSUFFICIENT_MODEL_STORAGE")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    buffer.fill(0)
                    output.fd.sync()
                }
            }
            require(total > 0L) { "EMPTY_MODEL" }
            if (expectedSizeBytes != null && total != expectedSizeBytes) throw IllegalArgumentException("MODEL_SIZE_MISMATCH")
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(root, "$id.litertlm").canonicalFile
            require(target.parentFile == root) { "BAD_MODEL_PATH" }
            val inspected = if (target.exists()) {
                part.delete()
                inspector.inspect(target, id)
            } else {
                val facts = inspector.inspect(part, id)
                if (!part.renameTo(target)) throw IllegalStateException("MODEL_INSTALL_FAILED")
                newlyInstalledTarget = target
                facts
            }
            writeDisplayName(root, id, safeName)
            return ImportedLocalAIModel(id, safeName, target, target.length(), inspected)
        } catch (error: Throwable) {
            part.delete()
            newlyInstalledTarget?.delete()
            throw error
        }
    }

    /** SAF cancel is represented as null; no file or model state is changed. */
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
    fun listModels(): List<ImportedLocalAIModel> {
        val root = rootDirectory.canonicalFile
        if (!root.isDirectory) return emptyList()
        return root.listFiles()?.asSequence()
            ?.filter { file -> file.isFile && file.name.matches(Regex("^[a-f0-9]{64}\\.litertlm$")) &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false) }
            ?.map { file ->
                val id = file.name.removeSuffix(".litertlm")
                val inspection = runCatching { inspector.inspect(file, id) }
                ImportedLocalAIModel(
                    id = id,
                    displayName = readDisplayName(root, id) ?: "Imported model ${id.take(12)}",
                    file = file,
                    sizeBytes = file.length(),
                    inspection = inspection.getOrNull(),
                    inspectionError = inspection.exceptionOrNull()?.message ?: if (inspection.isFailure) "MODEL_INSPECTION_FAILED" else null
                )
            }
            ?.sortedBy { it.displayName.lowercase() }
            ?.toList() ?: emptyList()
    }

    @Synchronized
    fun findModel(id: String): ImportedLocalAIModel? {
        if (!id.matches(Regex("^[a-f0-9]{64}$"))) return null
        return listModels().firstOrNull { it.id == id }
    }

    @Synchronized
    fun deleteModel(id: String): Boolean {
        if (!id.matches(Regex("^[a-f0-9]{64}$"))) return false
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return false
        if (!root.isDirectory) return true
        val model = File(root, "$id.litertlm").canonicalFile
        val metadata = File(root, "$id.name").canonicalFile
        if (model.parentFile != root || metadata.parentFile != root) return false
        val modelDeleted = !model.exists() || (model.isFile && model.delete())
        val metadataDeleted = !metadata.exists() || (metadata.isFile && metadata.delete())
        return modelDeleted && metadataDeleted
    }

    @Synchronized
    fun cleanupIncompleteImports(): Int {
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return 0
        if (!root.isDirectory) return 0
        var removed = 0
        root.listFiles()?.filter { file -> file.isFile && file.name.matches(Regex("^(import-[a-f0-9-]+|[a-f0-9]{64}\\.name)\\.part$")) &&
            runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false) }?.forEach { file ->
            if (file.delete()) removed++
        }
        root.listFiles()?.filter { file ->
            val id = file.name.removeSuffix(".name")
            val model = File(root, "$id.litertlm")
            file.isFile && id.matches(Regex("^[a-f0-9]{64}$")) &&
                runCatching { file.canonicalFile.parentFile == root && model.canonicalFile.parentFile == root }.getOrDefault(false) &&
                !model.isFile
        }?.forEach { file ->
            if (file.delete()) removed++
        }
        return removed
    }

    @Synchronized
    fun clear(): Boolean {
        if (!rootDirectory.exists()) return true
        val root = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return false
        val ok = root.listFiles()?.all { child ->
            runCatching { child.canonicalFile.parentFile == root && child.delete() }.getOrDefault(false)
        } ?: true
        return ok && (!root.exists() || root.delete())
    }

    private fun writeDisplayName(root: File, id: String, name: String) {
        val target = File(root, "$id.name").canonicalFile
        require(target.parentFile == root) { "BAD_MODEL_PATH" }
        val temporary = File(root, "$id.name.part").canonicalFile
        require(temporary.parentFile == root) { "BAD_MODEL_PATH" }
        try {
            FileOutputStream(temporary).use { out ->
                out.write(name.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!temporary.renameTo(target)) throw IllegalStateException("MODEL_METADATA_WRITE_FAILED")
        } finally {
            temporary.delete()
        }
    }

    private fun readDisplayName(root: File, id: String): String? {
        val file = File(root, "$id.name").canonicalFile
        if (file.parentFile != root || !file.isFile || file.length() !in 1..256) return null
        return runCatching { file.readText(Charsets.UTF_8).takeIf { it == sanitizeModelFilename(it) } }.getOrNull()
    }

    private fun sanitizeModelFilename(value: String): String {
        val leaf = value.replace('\\', '/').substringAfterLast('/').trim()
        require(leaf.isNotEmpty() && leaf.length <= 180 && !leaf.any { it.code < 0x20 || it == '\u007f' }) { "BAD_MODEL_FILENAME" }
        require(leaf.none { it in "<>:\"|?*" }) { "BAD_MODEL_FILENAME" }
        return leaf
    }

    companion object {
        const val MAX_MODEL_BYTES = 8L * 1024L * 1024L * 1024L
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
