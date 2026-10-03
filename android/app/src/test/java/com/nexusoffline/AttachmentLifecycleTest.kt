package com.nexusoffline

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentLifecycleTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    @Test fun successfulExportCopiesBytesAndDeletesPlaintextTemporary() {
        val clear = ByteArray(8193) { (it * 7).toByte() }
        val temp = File(temporaryFolder.root, "opened.tmp").apply { writeBytes(clear) }
        val destination = ByteArrayOutputStream()
        AttachmentExportCopy.copyAndDeleteTemporary(temp, destination)
        assertArrayEquals(clear, destination.toByteArray())
        assertFalse(temp.exists())
    }

    @Test fun failedExportStillDeletesPlaintextTemporary() {
        val temp = File(temporaryFolder.root, "failed-export.tmp").apply { writeText("private") }
        val failing = object : OutputStream() {
            override fun write(value: Int) { throw IOException("simulated document provider failure") }
        }
        assertThrows(IOException::class.java) { AttachmentExportCopy.copyAndDeleteTemporary(temp, failing) }
        assertFalse(temp.exists())
    }

    @Test fun clearDataRemovesAllPrivateAttachmentDirectories() {
        val encrypted = File(temporaryFolder.root, "encrypted").apply { mkdirs() }
        val legacy = File(temporaryFolder.root, "legacy").apply { mkdirs() }
        val cache = File(temporaryFolder.root, "share").apply { mkdirs() }
        File(encrypted, "record.nxa").writeText("ciphertext")
        File(legacy, "plain.txt").writeText("plaintext")
        File(cache, "open.tmp").writeText("temporary plaintext")
        assertTrue(AttachmentFileCleanup.clearDirectories(encrypted, legacy, cache))
        assertFalse(encrypted.exists())
        assertFalse(legacy.exists())
        assertFalse(cache.exists())
    }

    @Test fun deleteTransferTempsRemovesOnlyFilesOwnedByThatAttachment() {
        val cache = File(temporaryFolder.root, "share").apply { mkdirs() }
        val transferId = "0123456789abcdef0123456789abcdef"
        val temp = File(cache, "$transferId-report.pdf").apply { writeText("plaintext") }
        val other = File(cache, "fedcba9876543210fedcba9876543210-other.pdf").apply { writeText("keep") }
        assertTrue(AttachmentFileCleanup.deleteTransferTemps(cache, transferId))
        assertFalse(temp.exists())
        assertTrue(other.isFile)
        assertFalse(AttachmentFileCleanup.deleteTransferTemps(cache, "../private"))
    }

    @Test fun safeAttachmentTempNameCannotEscapePrivateDirectory() {
        val directory = File(temporaryFolder.root, "share").apply { mkdirs() }
        val transferId = "0123456789abcdef0123456789abcdef"
        val file = AttachmentTempFiles.safeOutput(directory, transferId, "report.pdf")
        assertEquals(directory.canonicalFile, file.canonicalFile.parentFile)
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentTempFiles.safeOutput(directory, transferId, "../private")
        }
    }
}
