package com.nexusoffline

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Structural-test fixture only. Its sections are placeholders and are never loaded for inference. */
internal object LiteRtLmContainerFixture {
    private const val FILE_BYTES = 65_536
    private const val HEADER_END = 512
    private val sectionStarts = longArrayOf(16_384, 32_768, 49_152)

    fun bytes(
        majorVersion: Int = 1,
        sectionTypes: IntArray = intArrayOf(3, 4, 5),
        architecture: String? = null
    ): ByteArray {
        require(sectionTypes.size == 3)
        val data = ByteArray(FILE_BYTES)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(0)
        buffer.put("LITERTLM".toByteArray(Charsets.US_ASCII))
        buffer.putInt(majorVersion)
        buffer.putInt(0) // minor
        buffer.putInt(0) // patch
        buffer.putInt(0) // reserved
        buffer.putLong(HEADER_END.toLong())

        // Root LiteRTLMMetaData table at absolute offset 44; vtable at 36.
        buffer.putInt(32, 12)
        buffer.putShortInt(36, 8)
        buffer.putShortInt(38, 12)
        buffer.putShortInt(40, 4)
        buffer.putShortInt(42, 8)
        buffer.putInt(44, 8)
        buffer.putInt(48, 16) // system metadata at 64
        buffer.putInt(52, 28) // section metadata at 80

        // SystemMetadata table, vtable at 56 and object at 64.
        buffer.putShortInt(56, if (architecture == null) 4 else 6)
        buffer.putShortInt(58, if (architecture == null) 4 else 8)
        buffer.putInt(64, 8)
        if (architecture != null) {
            buffer.putShortInt(60, 4) // metadata vector field
            buffer.putInt(68, 188) // vector at 256
            buffer.putInt(256, 1)
            buffer.putInt(260, 20) // metadata pair table at 280
            buffer.putShortInt(264, 10)
            buffer.putShortInt(266, 16)
            buffer.putShortInt(268, 4)
            buffer.putShortInt(270, 8)
            buffer.putShortInt(272, 12)
            buffer.putInt(280, 16)
            buffer.putInt(284, 36) // key string at 320
            buffer.put(288, 9.toByte()) // string value type
            buffer.putInt(292, 60) // string value table at 352
            buffer.putStringAt(320, "architecture")
            buffer.putShortInt(344, 6)
            buffer.putShortInt(346, 8)
            buffer.putShortInt(348, 4)
            buffer.putInt(352, 8)
            buffer.putInt(356, 28) // architecture string at 384
            buffer.putStringAt(384, architecture)
        }

        // SectionMetadata table at 80 points at a three-element object vector at 96.
        buffer.putShortInt(72, 6)
        buffer.putShortInt(74, 8)
        buffer.putShortInt(76, 4)
        buffer.putInt(80, 8)
        buffer.putInt(84, 12)
        buffer.putInt(96, 3)
        buffer.putInt(100, 32) // SectionObject at 132
        buffer.putInt(104, 68) // SectionObject at 172
        buffer.putInt(108, 104) // SectionObject at 212

        val tableStarts = longArrayOf(132, 172, 212)
        val vtableStarts = longArrayOf(120, 160, 200)
        for (index in sectionTypes.indices) {
            val table = tableStarts[index].toInt()
            val vtable = vtableStarts[index].toInt()
            buffer.putShortInt(vtable, 12)
            buffer.putShortInt(vtable + 2, 24)
            buffer.putShortInt(vtable + 4, 0) // optional per-section items
            buffer.putShortInt(vtable + 6, 4) // begin_offset
            buffer.putShortInt(vtable + 8, 12) // end_offset
            buffer.putShortInt(vtable + 10, 20) // data_type
            buffer.putInt(table, 12)
            val begin = sectionStarts[index]
            buffer.putLong(table + 4, begin)
            buffer.putLong(table + 12, begin + 8)
            buffer.put(table + 20, sectionTypes[index].toByte())
            if (sectionTypes[index] == 3) {
                data[(begin + 4).toInt()] = 'T'.code.toByte()
                data[(begin + 5).toInt()] = 'F'.code.toByte()
                data[(begin + 6).toInt()] = 'L'.code.toByte()
                data[(begin + 7).toInt()] = '3'.code.toByte()
            }
        }
        return data
    }

    fun write(
        file: File,
        majorVersion: Int = 1,
        sectionTypes: IntArray = intArrayOf(3, 4, 5),
        architecture: String? = null
    ): File {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes(majorVersion, sectionTypes, architecture))
        return file
    }

    private fun ByteBuffer.putShortInt(index: Int, value: Int) = putShort(index, value.toShort())

    private fun ByteBuffer.putStringAt(index: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        putInt(index, bytes.size)
        bytes.forEachIndexed { offset, byte -> put(index + 4 + offset, byte) }
        put(index + 4 + bytes.size, 0.toByte())
    }
}
