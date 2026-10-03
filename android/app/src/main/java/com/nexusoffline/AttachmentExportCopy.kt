package com.nexusoffline

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

internal object AttachmentExportCopy {
    /** Copies only the already-authenticated temporary attachment and always removes its plaintext staging file. */
    fun copyAndDeleteTemporary(plaintext: File, destination: OutputStream) {
        require(plaintext.isFile) { "PLAINTEXT_TEMP_MISSING" }
        try {
            FileInputStream(plaintext).use { input -> input.copyTo(destination, 64 * 1024) }
            destination.flush()
        } finally {
            plaintext.delete()
        }
    }
}

internal object AttachmentFileCleanup {
    fun deleteTransferTemps(directory: File, transferId: String): Boolean {
        if (!NativeProtocol.isTransferId(transferId) || !directory.isDirectory) return false
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        var allRemoved = true
        root.listFiles()?.filter { file ->
            file.isFile && file.name.startsWith("$transferId-") &&
                runCatching { file.canonicalFile.parentFile == root }.getOrDefault(false)
        }?.forEach { file -> if (!file.delete() && file.exists()) allRemoved = false }
        return allRemoved
    }

    fun clearDirectories(vararg directories: File): Boolean {
        var allRemoved = true
        directories.distinct().forEach { directory ->
            if (directory.exists() && !directory.deleteRecursively()) allRemoved = false
        }
        return allRemoved
    }
}
