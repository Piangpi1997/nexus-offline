package com.nexusoffline

import java.io.ByteArrayInputStream
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedLocalAIModelTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    private fun setupStore(name: String = "models", keys: LocalAIModelKeyProvider = TestLocalAIModelKeyProvider()): LocalAIModelStore {
        val root = File(temporaryFolder.root, name)
        return LocalAIModelStore(
            rootDirectory = root,
            availableBytes = { Long.MAX_VALUE },
            keyProvider = keys,
            plaintextStageDirectory = File(temporaryFolder.root, "$name-plaintext-stage")
        )
    }

    @Test fun primaryContainerAndMetadataDoNotContainPlaintextAndStagingIsRemovedAfterUse() {
        val modelBytes = LiteRtLmContainerFixture.bytes()
        val store = setupStore()
        val imported = store.importModel("sensitive-model-label.litertlm", ByteArrayInputStream(modelBytes))
        assertTrue(imported.file.name.endsWith(".nxm"))
        val ciphertext = imported.file.readBytes()
        assertFalse(contains(ciphertext, modelBytes))
        assertFalse(contains(ciphertext, "sensitive-model-label.litertlm".toByteArray()))
        assertTrue(File(temporaryFolder.root, "models-plaintext-stage").listFiles().isNullOrEmpty())

        val staged = store.stageModelForRuntime(imported.id)
        assertEquals(modelBytes.toList(), staged.readBytes().toList())
        assertTrue(staged.isFile)
        assertTrue(store.cleanupRuntimeStage(staged))
        assertFalse(staged.exists())
    }

    @Test fun chunkTamperingRejectsStageAndDeletesEveryPlaintextPartial() {
        val stageRoot = File(temporaryFolder.root, "models-plaintext-stage")
        val store = setupStore()
        val imported = store.importModel("tamper.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        java.io.RandomAccessFile(imported.file, "rw").use { file ->
            val firstChunkCipherOffset = 4L + 1 + 32 + 8 + 4 + 7
            file.seek(firstChunkCipherOffset)
            val original = file.readByte()
            file.seek(firstChunkCipherOffset)
            file.writeByte(original.toInt() xor 0x20)
        }
        val error = assertThrows(IllegalArgumentException::class.java) { store.stageModelForRuntime(imported.id) }
        assertEquals("MODEL_CHUNK_AUTHENTICATION_FAILED", error.message)
        assertTrue(stageRoot.listFiles().isNullOrEmpty())
    }

    @Test fun truncatedContainerIsRejectedAndLeavesNoStageFile() {
        val store = setupStore()
        val imported = store.importModel("truncated.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        java.io.RandomAccessFile(imported.file, "rw").use { file -> file.setLength(file.length() - 5L) }
        val error = assertThrows(IllegalStateException::class.java) { store.stageModelForRuntime(imported.id) }
        assertEquals("MODEL_METADATA_FOOTER_INVALID", error.message)
        assertTrue(File(temporaryFolder.root, "models-plaintext-stage").listFiles().isNullOrEmpty())
    }

    @Test fun wrongContainerMagicIsRejectedBeforeRuntimeAndLeavesNoStageFile() {
        val store = setupStore()
        val imported = store.importModel("wrong-format.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        java.io.RandomAccessFile(imported.file, "rw").use { file ->
            file.seek(0L)
            file.writeByte(0x00)
        }
        val error = assertThrows(IllegalStateException::class.java) { store.stageModelForRuntime(imported.id) }
        assertEquals("MODEL_ENVELOPE_FORMAT", error.message)
        assertTrue(File(temporaryFolder.root, "models-plaintext-stage").listFiles().isNullOrEmpty())
    }

    @Test fun missingKeystoreKeyIsReportedWithoutCreatingPlaintext() {
        val keys = TestLocalAIModelKeyProvider()
        val store = setupStore(keys = keys)
        val imported = store.importModel("key-missing.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val keyId = EncryptedLocalAIModel.readHeader(imported.file).keyId
        assertTrue(keys.delete(keyId))
        val listed = store.findModel(imported.id)!!
        assertEquals("MODEL_KEY_MISSING", listed.inspectionError)
        val error = assertThrows(IllegalStateException::class.java) { store.stageModelForRuntime(imported.id) }
        assertEquals("MODEL_KEY_MISSING", error.message)
        assertTrue(File(temporaryFolder.root, "models-plaintext-stage").listFiles().isNullOrEmpty())
    }

    @Test fun unavailableKeystoreDuringImportLeavesNoEncryptedOrPlaintextPart() {
        val unavailable = object : LocalAIModelKeyProvider {
            override fun create(keyId: String): SecretKey = throw GeneralSecurityException("keystore unavailable")
            override fun get(keyId: String): SecretKey? = null
            override fun delete(keyId: String) = true
            override fun deleteOrphaned(activeKeyIds: Set<String>) = true
            override fun deleteAll() = true
        }
        val store = setupStore(keys = unavailable)
        assertThrows(GeneralSecurityException::class.java) {
            store.importModel("unavailable.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        }
        assertTrue(File(temporaryFolder.root, "models").listFiles().isNullOrEmpty())
        assertTrue(File(temporaryFolder.root, "models-plaintext-stage").listFiles().isNullOrEmpty())
    }

    @Test fun duplicateImportKeepsOneCommittedContainerAndAuthenticatedDisplayName() {
        val store = setupStore()
        val bytes = LiteRtLmContainerFixture.bytes()
        val first = store.importModel("first.litertlm", ByteArrayInputStream(bytes))
        val duplicate = store.importModel("second.litertlm", ByteArrayInputStream(bytes))
        assertEquals(first.id, duplicate.id)
        assertEquals("first.litertlm", duplicate.displayName)
        assertEquals(1, File(temporaryFolder.root, "models").listFiles().orEmpty().count { it.name.endsWith(".nxm") })
        assertEquals(1, store.listModels().size)
    }

    @Test fun encryptedMetadataCorruptionIsNotTrustedForInspection() {
        val store = setupStore()
        val imported = store.importModel("metadata.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        java.io.RandomAccessFile(imported.file, "rw").use { file ->
            file.seek(file.length() - 9L)
            val original = file.readByte()
            file.seek(file.length() - 9L)
            file.writeByte(original.toInt() xor 0x01)
        }
        val listed = store.findModel(imported.id)!!
        assertNull(listed.inspection)
        assertNotNull(listed.inspectionError)
        assertEquals("MODEL_METADATA_AUTHENTICATION_FAILED", listed.inspectionError)
    }

    @Test fun processRestartCleansResidualPlaintextStageButKeepsAuthenticatedModel() {
        val keys = TestLocalAIModelKeyProvider()
        val store = setupStore(keys = keys)
        val imported = store.importModel("restart.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val staleStage = File(temporaryFolder.root, "models-plaintext-stage/stale.litertlm").apply { writeText("plaintext") }
        val restartedStore = setupStore(keys = keys)
        assertEquals(1, restartedStore.cleanupIncompleteImports())
        assertFalse(staleStage.exists())
        assertEquals(imported.id, restartedStore.findModel(imported.id)?.id)
    }

    @Test fun lowStoragePreventsPlaintextStageCreation() {
        val freeBytes = java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE)
        val root = File(temporaryFolder.root, "low-space-models")
        val store = LocalAIModelStore(
            rootDirectory = root,
            availableBytes = freeBytes::get,
            keyProvider = TestLocalAIModelKeyProvider(),
            plaintextStageDirectory = File(temporaryFolder.root, "low-space-stage")
        )
        val imported = store.importModel("low-space.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        freeBytes.set(0L)
        val error = assertThrows(IllegalStateException::class.java) { store.stageModelForRuntime(imported.id) }
        assertEquals("INSUFFICIENT_MODEL_STORAGE", error.message)
        assertTrue(File(temporaryFolder.root, "low-space-stage").listFiles().isNullOrEmpty())
    }

    @Test fun modelDeleteAndClearDataRemovePerModelKeystoreKeys() {
        val keys = TestLocalAIModelKeyProvider()
        val store = setupStore(keys = keys)
        val first = store.importModel("delete-key.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val firstKeyId = EncryptedLocalAIModel.readHeader(first.file).keyId
        assertNotNull(keys.get(firstKeyId))
        assertTrue(store.deleteModel(first.id))
        assertNull(keys.get(firstKeyId))

        val second = store.importModel("clear-key.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes(architecture = "other")))
        val secondKeyId = EncryptedLocalAIModel.readHeader(second.file).keyId
        val staged = store.stageModelForRuntime(second.id)
        assertTrue(store.clear())
        assertNull(keys.get(secondKeyId))
        assertFalse(staged.exists())
        assertTrue(store.listModels().isEmpty())
    }

    @Test fun keystoreReadFailureIsNotMisreportedAsMissingKey() {
        val workingKeys = TestLocalAIModelKeyProvider()
        val writer = setupStore(name = "unavailable-models", keys = workingKeys)
        val imported = writer.importModel("keystore-lost.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val unavailable = object : LocalAIModelKeyProvider {
            override fun create(keyId: String): SecretKey = error("not used")
            override fun get(keyId: String): SecretKey? = throw GeneralSecurityException("Keystore locked")
            override fun delete(keyId: String) = false
            override fun deleteOrphaned(activeKeyIds: Set<String>) = false
            override fun deleteAll() = false
        }
        val reader = setupStore(name = "unavailable-models", keys = unavailable)
        assertEquals("MODEL_KEY_UNAVAILABLE", reader.findModel(imported.id)?.inspectionError)
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { index -> haystack[offset + index] == needle[index] }
        }
    }
}
