package com.nexusoffline

import java.io.File
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedAttachmentCipherTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    @Test fun encryptsAndDecryptsStreamedAttachment() {
        val clear = ByteArray(300_123) { (it * 31).toByte() }
        val source = File(temporaryFolder.root, "source.bin").apply { writeBytes(clear) }
        val encrypted = File(temporaryFolder.root, "stored.nxa")
        val recovered = File(temporaryFolder.root, "recovered.bin")
        val key = newKey()

        EncryptedAttachmentCipher.encrypt(source, encrypted, TRANSFER_ID, "field-notes.bin", key)
        assertFalse(encrypted.readBytes().contentEquals(clear))
        val written = EncryptedAttachmentCipher.decrypt(encrypted, recovered, TRANSFER_ID, "field-notes.bin", key)
        assertArrayEquals(clear, recovered.readBytes())
        assertTrue(written == clear.size.toLong())
    }

    @Test fun tamperedCiphertextIsRejectedAndPartialPlaintextRemoved() {
        val source = File(temporaryFolder.root, "source.txt").apply { writeText("private attachment content") }
        val encrypted = File(temporaryFolder.root, "stored.nxa")
        val key = newKey()
        EncryptedAttachmentCipher.encrypt(source, encrypted, TRANSFER_ID, "private.txt", key)
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        encrypted.writeBytes(bytes)
        val output = File(temporaryFolder.root, "partial.txt")

        assertThrows(Exception::class.java) {
            EncryptedAttachmentCipher.decrypt(encrypted, output, TRANSFER_ID, "private.txt", key)
        }
        assertFalse(output.exists())
    }

    @Test fun metadataIsAuthenticatedAndUnsafeNamesAreRejected() {
        val source = File(temporaryFolder.root, "source.txt").apply { writeText("bound metadata") }
        val encrypted = File(temporaryFolder.root, "stored.nxa")
        val key = newKey()
        EncryptedAttachmentCipher.encrypt(source, encrypted, TRANSFER_ID, "private.txt", key)
        val output = File(temporaryFolder.root, "wrong-metadata.txt")

        assertThrows(Exception::class.java) {
            EncryptedAttachmentCipher.decrypt(encrypted, output, "abcdefabcdefabcdefabcdefabcdefab", "private.txt", key)
        }
        assertFalse(output.exists())
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedAttachmentCipher.encrypt(source, File(temporaryFolder.root, "unsafe.nxa"), TRANSFER_ID, "../private.txt", key)
        }
    }

    @Test fun wrongKeyCannotDecryptAttachment() {
        val source = File(temporaryFolder.root, "source.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
        val encrypted = File(temporaryFolder.root, "stored.nxa")
        val key = newKey()
        EncryptedAttachmentCipher.encrypt(source, encrypted, TRANSFER_ID, "safe.bin", key)
        val output = File(temporaryFolder.root, "wrong-key.bin")

        assertThrows(Exception::class.java) {
            EncryptedAttachmentCipher.decrypt(encrypted, output, TRANSFER_ID, "safe.bin", newKey())
        }
        assertFalse(output.exists())
        assertTrue(encrypted.isFile)
    }

    @Test fun verifyAuthenticatesCiphertextAndReturnsPlaintextHashWithoutOutputFile() {
        val clear = ByteArray(4097) { (it * 13).toByte() }
        val source = File(temporaryFolder.root, "source.bin").apply { writeBytes(clear) }
        val encrypted = File(temporaryFolder.root, "stored.nxa")
        val key = newKey()
        EncryptedAttachmentCipher.encrypt(source, encrypted, TRANSFER_ID, "safe.bin", key)

        val verification = EncryptedAttachmentCipher.verify(encrypted, TRANSFER_ID, "safe.bin", key)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(clear).joinToString("") { "%02x".format(it) }
        assertEquals(clear.size.toLong(), verification.plaintextBytes)
        assertEquals(expectedHash, verification.sha256)
        assertEquals(listOf("source.bin", "stored.nxa"), temporaryFolder.root.list()?.sorted())

        val bytes = encrypted.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        encrypted.writeBytes(bytes)
        assertThrows(Exception::class.java) { EncryptedAttachmentCipher.verify(encrypted, TRANSFER_ID, "safe.bin", key) }
        assertTrue(encrypted.isFile)
    }

    @Test fun missingEncryptedFileCannotBeListedAsVerified() {
        val missing = File(temporaryFolder.root, "missing.nxa")
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedAttachmentCipher.verify(missing, TRANSFER_ID, "safe.bin", newKey())
        }
    }

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun assertEquals(expected: Any?, actual: Any?) = org.junit.Assert.assertEquals(expected, actual)
    private companion object { const val TRANSFER_ID = "0123456789abcdef0123456789abcdef" }
}
