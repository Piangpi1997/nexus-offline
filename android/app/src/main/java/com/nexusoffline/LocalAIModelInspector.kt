package com.nexusoffline

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Facts established by bounded inspection of a local .litertlm container. */
internal data class LocalAIModelInspection(
    val format: String,
    val formatVersion: String,
    val modelName: String?,
    val architecture: String?,
    val contextLength: Int?,
    val license: String?,
    val estimatedRamBytes: Long,
    val sectionTypes: List<String>,
    val structurallyCompatible: Boolean,
    val compatibility: String
)

/**
 * Inspects only the fixed-size container prefix, its bounded FlatBuffers header,
 * and the first bytes of the declared TFLite model section. It never extracts,
 * executes, or allocates model-sized data. LiteRT-LM remains the final validator.
 */
internal class LocalAIModelInspector {
    fun inspect(file: File, contentSha256: String? = null): LocalAIModelInspection {
        if (!file.isFile) throw IllegalArgumentException("MODEL_FILE_UNAVAILABLE")
        val fileSize = file.length()
        if (fileSize < MIN_CONTAINER_BYTES) throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
        if (fileSize > LocalAIModelStore.MAX_MODEL_BYTES) throw IllegalArgumentException("MODEL_TOO_LARGE")

        RandomAccessFile(file, "r").use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("UNSUPPORTED_MODEL_FORMAT")

            val major = readU32(input)
            val minor = readU32(input)
            val patch = readU32(input)
            input.seek(24L) // Header-end offset follows the 4-byte reserved field.
            val headerEnd = readU64(input)
            if (major != SUPPORTED_MAJOR) throw IllegalArgumentException("UNSUPPORTED_MODEL_VERSION")
            if (headerEnd < MIN_HEADER_END || headerEnd > fileSize || headerEnd > MAX_HEADER_END) {
                throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
            }

            val fb = BoundedFlatBuffer(input, METADATA_START, headerEnd)
            val root = fb.rootTable()
            val sectionMetadata = fb.indirectTableField(root, 1)
                ?: throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
            val sectionVector = fb.indirectVectorField(sectionMetadata, 0, MAX_SECTIONS)
                ?: throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
            if (sectionVector.count == 0) throw IllegalArgumentException("UNSUPPORTED_MODEL_CONTENT")

            val sections = ArrayList<Section>(sectionVector.count)
            for (index in 0 until sectionVector.count) {
                val objectTable = fb.vectorTable(sectionVector, index)
                val begin = fb.unsignedLongField(objectTable, 1)
                val end = fb.unsignedLongField(objectTable, 2)
                val type = fb.unsignedByteField(objectTable, 3)
                if (begin < headerEnd || end <= begin || end > fileSize) {
                    throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
                }
                sections += Section(begin, end, type)
            }
            val ordered = sections.sortedBy { it.begin }
            if (ordered.zipWithNext().any { (left, right) -> left.end > right.begin }) {
                throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
            }

            val modelSection = sections.firstOrNull { it.type == TYPE_TFLITE_MODEL }
                ?: throw IllegalArgumentException("UNSUPPORTED_MODEL_CONTENT")
            if (modelSection.end - modelSection.begin < TFLITE_IDENTIFIER_OFFSET + TFLITE_IDENTIFIER.size) {
                throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
            }
            input.seek(modelSection.begin + TFLITE_IDENTIFIER_OFFSET)
            val tfliteIdentifier = ByteArray(TFLITE_IDENTIFIER.size)
            input.readFully(tfliteIdentifier)
            if (!tfliteIdentifier.contentEquals(TFLITE_IDENTIFIER)) {
                throw IllegalArgumentException("INVALID_TFLITE_MODEL_SECTION")
            }

            val metadataValues = fb.stringMetadata(root)
            val tokenizerPresent = sections.any { it.type == TYPE_SENTENCEPIECE_TOKENIZER || it.type == TYPE_HF_TOKENIZER }
            val llmMetadataPresent = sections.any { it.type == TYPE_LLM_METADATA }
            if (!tokenizerPresent || !llmMetadataPresent) {
                throw IllegalArgumentException("UNSUPPORTED_MODEL_CONTENT")
            }

            val isKnownQwenArtifact = contentSha256.equals(QWEN3_06B_INT8_SHA256, ignoreCase = true)
            val modelName = if (isKnownQwenArtifact) "Qwen3-0.6B (dynamic INT8)" else
                metadataValues["model_name"] ?: metadataValues["name"]
            val architecture = if (isKnownQwenArtifact) "Qwen3 dense decoder · 0.6B parameters" else
                metadataValues["architecture"] ?: metadataValues["model_architecture"]
            val contextLength = if (isKnownQwenArtifact) 4096 else
                metadataValues["context_length"]?.toIntOrNull()?.takeIf { it in 1..1_000_000 }
            val license = if (isKnownQwenArtifact) "Apache-2.0 (artifact fingerprint matched)" else null
            val estimatedRam = if (isKnownQwenArtifact) QWEN3_06B_INT8_RAM_ESTIMATE_BYTES else
                estimateRamBytes(fileSize)
            val names = sections.mapNotNull { SECTION_NAMES[it.type] }.distinct().sorted()
            return LocalAIModelInspection(
                format = "LiteRT-LM container",
                formatVersion = "$major.$minor.$patch",
                modelName = modelName?.take(MAX_METADATA_TEXT),
                architecture = architecture?.take(MAX_METADATA_TEXT),
                contextLength = contextLength,
                license = license,
                estimatedRamBytes = estimatedRam,
                sectionTypes = names,
                structurallyCompatible = true,
                compatibility = "STRUCTURE_OK_RUNTIME_LOAD_REQUIRED"
            )
        }
    }

    private data class Section(val begin: Long, val end: Long, val type: Int)

    private fun estimateRamBytes(modelBytes: Long): Long {
        // Planning estimate only (4.5× model bytes). Actual working memory depends on
        // model metadata, context/KV cache, backend, runtime and Android memory pressure.
        if (modelBytes > Long.MAX_VALUE / RAM_ESTIMATE_MULTIPLIER_NUMERATOR) return Long.MAX_VALUE
        return (modelBytes * RAM_ESTIMATE_MULTIPLIER_NUMERATOR + RAM_ESTIMATE_MULTIPLIER_DENOMINATOR - 1) /
            RAM_ESTIMATE_MULTIPLIER_DENOMINATOR
    }

    private fun readU32(input: RandomAccessFile): Long {
        val b0 = input.readUnsignedByte().toLong()
        val b1 = input.readUnsignedByte().toLong()
        val b2 = input.readUnsignedByte().toLong()
        val b3 = input.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readU64(input: RandomAccessFile): Long {
        val low = readU32(input)
        val high = readU32(input)
        if (high > Int.MAX_VALUE.toLong()) throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
        return (high shl 32) or low
    }

    private class BoundedFlatBuffer(
        private val input: RandomAccessFile,
        private val start: Long,
        private val limit: Long
    ) {
        data class Table(val address: Long, val vtable: Long, val vtableSize: Int, val objectSize: Int)
        data class Vector(val data: Long, val count: Int)

        fun rootTable(): Table {
            val relative = u32(start)
            if (relative < 4L) corrupt()
            return table(add(start, relative))
        }

        fun indirectTableField(table: Table, slot: Int): Table? = field(table, slot)?.let { table(indirect(it)) }

        fun indirectVectorField(table: Table, slot: Int, maximumCount: Int): Vector? =
            field(table, slot)?.let { vector(indirect(it), maximumCount) }

        fun vectorTable(vector: Vector, index: Int): Table {
            if (index !in 0 until vector.count) corrupt()
            val entry = vector.data + index.toLong() * 4L
            return table(indirect(entry))
        }

        fun unsignedLongField(table: Table, slot: Int): Long {
            val address = field(table, slot) ?: return 0L
            requireObjectBytes(table, address, 8)
            return u64(address)
        }

        fun unsignedByteField(table: Table, slot: Int): Int {
            val address = field(table, slot) ?: return 0
            requireObjectBytes(table, address, 1)
            return byte(address)
        }

        fun stringMetadata(root: Table): Map<String, String> {
            val system = indirectTableField(root, 0) ?: return emptyMap()
            val entries = indirectVectorField(system, 0, MAX_METADATA_ENTRIES) ?: return emptyMap()
            val values = LinkedHashMap<String, String>()
            for (index in 0 until entries.count) {
                val pair = vectorTable(entries, index)
                val keyField = field(pair, 0) ?: continue
                val key = string(indirect(keyField))?.lowercase(Locale.ROOT) ?: continue
                val type = field(pair, 1)?.let(::byte) ?: 0
                val valueField = field(pair, 2) ?: continue
                val valueTable = table(indirect(valueField))
                val value = when (type) {
                    TYPE_STRING_VALUE -> field(valueTable, 0)?.let { string(indirect(it)) }
                    TYPE_UINT32_VALUE -> field(valueTable, 0)?.let { u32(it).toString() }
                    TYPE_INT32_VALUE -> field(valueTable, 0)?.let { i32(it).toString() }
                    else -> null
                }
                if (value != null && value.length <= MAX_METADATA_TEXT) values[key] = value
            }
            return values
        }

        private fun table(address: Long): Table {
            checkRange(address, 4)
            val backOffset = i32(address).toLong()
            if (backOffset == 0L) corrupt()
            val vtableAddress = address - backOffset
            checkRange(vtableAddress, 4)
            val vtableSize = u16(vtableAddress).toInt()
            val objectSize = u16(vtableAddress + 2).toInt()
            if (vtableSize < 4 || vtableSize > MAX_VTABLE_BYTES || objectSize < 4 || objectSize > MAX_OBJECT_BYTES) corrupt()
            checkRange(vtableAddress, vtableSize.toLong())
            checkRange(address, objectSize.toLong())
            return Table(address, vtableAddress, vtableSize, objectSize)
        }

        private fun field(table: Table, slot: Int): Long? {
            if (slot < 0) corrupt()
            val entry = table.vtable + 4L + slot.toLong() * 2L
            if (entry + 2L > table.vtable + table.vtableSize) return null
            val offset = u16(entry).toInt()
            if (offset == 0) return null
            if (offset >= table.objectSize) corrupt()
            val address = table.address + offset
            checkRange(address, 1)
            return address
        }

        private fun vector(address: Long, maximumCount: Int): Vector {
            val countLong = u32(address)
            if (countLong > maximumCount.toLong()) corrupt()
            val count = countLong.toInt()
            val data = address + 4L
            checkRange(data, count.toLong() * 4L)
            return Vector(data, count)
        }

        private fun indirect(address: Long): Long {
            val offset = u32(address)
            if (offset == 0L) corrupt()
            return add(address, offset)
        }

        private fun string(address: Long): String? {
            val length = u32(address)
            if (length > MAX_METADATA_STRING_BYTES) corrupt()
            val bytesStart = address + 4L
            checkRange(bytesStart, length + 1L)
            if (byte(bytesStart + length) != 0) corrupt()
            return String(ByteArray(length.toInt()).also { bytes ->
                input.seek(bytesStart)
                input.readFully(bytes)
            }, StandardCharsets.UTF_8)
        }

        private fun requireObjectBytes(table: Table, address: Long, length: Int) {
            if (address < table.address || address + length > table.address + table.objectSize) corrupt()
            checkRange(address, length.toLong())
        }

        private fun add(left: Long, right: Long): Long {
            if (right < 0 || left > Long.MAX_VALUE - right) corrupt()
            return left + right
        }

        private fun checkRange(address: Long, length: Long) {
            if (address < start || length < 0 || address > limit || length > limit - address) corrupt()
        }

        private fun byte(address: Long): Int {
            checkRange(address, 1)
            input.seek(address)
            return input.readUnsignedByte()
        }

        private fun u16(address: Long): Long {
            checkRange(address, 2)
            input.seek(address)
            return input.readUnsignedByte().toLong() or (input.readUnsignedByte().toLong() shl 8)
        }

        private fun u32(address: Long): Long {
            checkRange(address, 4)
            input.seek(address)
            val b0 = input.readUnsignedByte().toLong()
            val b1 = input.readUnsignedByte().toLong()
            val b2 = input.readUnsignedByte().toLong()
            val b3 = input.readUnsignedByte().toLong()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        private fun u64(address: Long): Long {
            val low = u32(address)
            val high = u32(address + 4L)
            if (high > Int.MAX_VALUE.toLong()) corrupt()
            return (high shl 32) or low
        }

        private fun i32(address: Long): Int = u32(address).toInt()
        private fun corrupt(): Nothing = throw IllegalArgumentException("CORRUPT_MODEL_CONTAINER")
    }

    companion object {
        private val MAGIC = "LITERTLM".toByteArray(StandardCharsets.US_ASCII)
        private val TFLITE_IDENTIFIER = "TFL3".toByteArray(StandardCharsets.US_ASCII)
        private const val MIN_CONTAINER_BYTES = 40L
        private const val METADATA_START = 32L
        private const val MIN_HEADER_END = 40L
        private const val MAX_HEADER_END = 1_048_576L
        private const val MAX_SECTIONS = 64
        private const val MAX_METADATA_ENTRIES = 256
        private const val MAX_METADATA_STRING_BYTES = 256L
        private const val MAX_METADATA_TEXT = 128
        private const val MAX_VTABLE_BYTES = 1024
        private const val MAX_OBJECT_BYTES = 4096
        private const val TFLITE_IDENTIFIER_OFFSET = 4L
        private const val SUPPORTED_MAJOR = 1L
        private const val TYPE_TFLITE_MODEL = 3
        private const val TYPE_SENTENCEPIECE_TOKENIZER = 4
        private const val TYPE_LLM_METADATA = 5
        private const val TYPE_HF_TOKENIZER = 6
        private const val TYPE_STRING_VALUE = 9
        private const val TYPE_UINT32_VALUE = 5
        private const val TYPE_INT32_VALUE = 6
        private const val RAM_ESTIMATE_MULTIPLIER_NUMERATOR = 45L
        private const val RAM_ESTIMATE_MULTIPLIER_DENOMINATOR = 10L
        private const val QWEN3_06B_INT8_RAM_ESTIMATE_BYTES = 2_700_000_000L
        /** SHA-256 of the upstream Qwen3-0.6B dynamic INT8 .litertlm LFS object. */
        const val QWEN3_06B_INT8_SHA256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4"
        private val SECTION_NAMES = mapOf(
            1 to "Generic binary data",
            TYPE_TFLITE_MODEL to "TFLite model",
            TYPE_SENTENCEPIECE_TOKENIZER to "SentencePiece tokenizer",
            TYPE_LLM_METADATA to "LLM metadata",
            TYPE_HF_TOKENIZER to "Hugging Face tokenizer",
            7 to "TFLite external weights"
        )
    }
}
