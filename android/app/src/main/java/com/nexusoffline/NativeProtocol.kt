package com.nexusoffline

internal object NativeProtocol {
    const val MAX_DATA_BYTES = 30 * 1024
    const val MAX_FILE_BYTES = 256L * 1024 * 1024
    const val MAX_FILE_PAYLOAD_BYTES = MAX_FILE_BYTES + 16L // AES-GCM authentication tag

    fun acceptsPayloadSize(size: Int): Boolean = size in 1..MAX_DATA_BYTES
    fun acceptsWireType(type: String?): Boolean = type == "hello" || type == "ciphertext-v1"
    fun acceptsFileSize(size: Long): Boolean = size in 1..MAX_FILE_BYTES
    fun acceptsEncryptedFileSize(size: Long): Boolean = size in 17..MAX_FILE_PAYLOAD_BYTES

    fun sanitizeFilename(input: String): String {
        val leaf = input.replace('\\', '/').substringAfterLast('/')
        val cleaned = leaf
            .map { ch -> if (ch.code < 32 || ch.code == 127 || ch in "<>:\"|?*") '_' else ch }
            .joinToString("")
            .trim()
            .trim('.')
            .take(160)
        return cleaned.ifBlank { "file" }
    }

    fun isTransferId(value: String): Boolean = value.matches(Regex("^[A-Fa-f0-9]{32}$"))

    fun routeTargets(memberIds: Iterable<String>, senderId: String): List<String> =
        memberIds.asSequence()
            .filter { it.isNotBlank() && it != senderId }
            .distinct()
            .toList()
}
