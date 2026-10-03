package com.nexusoffline

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentMetadataTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()
    private val id = "0123456789abcdef0123456789abcdef"
    private val hash = "ab".repeat(32)

    @Test fun metadataRoundTripsAndIsStrictlyBounded() {
        val expected = StoredAttachmentMetadata(id, "report.pdf", 1234L, 1_800_000_000_000L, hash)
        val encoded = AttachmentMetadataCodec.encode(expected)
        assertEquals(expected, AttachmentMetadataCodec.decode(encoded))
        val extra = encoded + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { AttachmentMetadataCodec.decode(extra) }
        val tampered = encoded.copyOf().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) { AttachmentMetadataCodec.decode(tampered) }
    }

    @Test fun invalidIdsTraversalAndMetadataFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentMetadataCodec.encode(StoredAttachmentMetadata("../bad", "report.pdf", 1L, 1L, hash))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentMetadataCodec.encode(StoredAttachmentMetadata(id, "../report.pdf", 1L, 1L, hash))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentMetadataCodec.encode(StoredAttachmentMetadata(id, "report.pdf", 1L, 1L, "bad"))
        }
    }

    @Test fun failedLegacyMigrationPreservesTheOnlyPlaintextSource() {
        val source = File(temporaryFolder.root, "${id}_legacy.txt").apply { writeText("still available") }
        val result = LegacyAttachmentMigration.migrate(
            listOf(source),
            alreadyStored = { false },
            commitEncrypted = { _, _, _ -> throw IllegalStateException("simulated encrypted commit failure") }
        )
        assertEquals(0, result.migrated)
        assertEquals(1, result.remainingPlaintext)
        assertTrue(source.isFile)
        assertEquals("still available", source.readText())
    }

    @Test fun successfulLegacyMigrationDeletesSourceOnlyAfterCommit() {
        val source = File(temporaryFolder.root, "${id}_legacy.txt").apply { writeText("migrate me") }
        var committed = false
        val result = LegacyAttachmentMigration.migrate(
            listOf(source),
            alreadyStored = { false },
            commitEncrypted = { _, transferId, filename ->
                assertEquals(id, transferId)
                assertEquals("legacy.txt", filename)
                committed = true
            }
        )
        assertTrue(committed)
        assertEquals(1, result.migrated)
        assertFalse(source.exists())
    }

    @Test fun tempOutputRejectsTraversalAndCleanupRemovesOnlyExpiredFiles() {
        val directory = File(temporaryFolder.root, "exports").apply { mkdirs() }
        val output = AttachmentTempFiles.safeOutput(directory, id, "photo.jpg")
        assertEquals(directory.canonicalFile, output.parentFile?.canonicalFile)
        assertThrows(IllegalArgumentException::class.java) {
            AttachmentTempFiles.safeOutput(directory, id, "../../outside.txt")
        }
        val expired = File(directory, "expired.tmp").apply { writeText("temporary") }
        val recent = File(directory, "recent.tmp").apply { writeText("temporary") }
        expired.setLastModified(1_000L)
        recent.setLastModified(9_500L)
        assertEquals(1, AttachmentTempFiles.cleanupExpired(directory, nowMillis = 10_000L, maxAgeMillis = 2_000L))
        assertFalse(expired.exists())
        assertTrue(recent.exists())
    }
}
