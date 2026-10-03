package com.nexusoffline

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalAIModelStoreTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    @Test fun importsValidatedModelPrivatelyWithStableHashAndNoOriginalPath() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val modelBytes = LiteRtLmContainerFixture.bytes()
        val imported = store.importModel("/untrusted/path/Gemma3-1B.litertlm", ByteArrayInputStream(modelBytes))
        assertEquals(64, imported.id.length)
        assertTrue(imported.file.isFile)
        assertEquals(modelBytes.size.toLong(), imported.sizeBytes)
        assertEquals("Gemma3-1B.litertlm", imported.displayName)
        assertTrue(imported.inspection?.structurallyCompatible == true)
        assertEquals(imported.id, store.listModels().single().id)
        assertEquals(imported.id, store.importModel("Gemma3-1B.litertlm", ByteArrayInputStream(modelBytes)).id)
        assertTrue(store.clear())
        assertFalse(root.exists())
    }

    @Test fun rejectsUnsupportedFormatAndInsufficientSpace() {
        val root = File(temporaryFolder.root, "models")
        val noSpaceStore = newTestLocalAIStore(root) { 0L }
        assertEquals("UNSUPPORTED_MODEL_FORMAT", assertThrows(IllegalArgumentException::class.java) {
            noSpaceStore.importModel("model.bin", ByteArrayInputStream(byteArrayOf(1)))
        }.message)
        assertEquals("INSUFFICIENT_MODEL_STORAGE", assertThrows(IllegalStateException::class.java) {
            noSpaceStore.importModel("model.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        }.message)
        assertThrows(IllegalArgumentException::class.java) {
            newTestLocalAIStore(root) { Long.MAX_VALUE }.importModel("../model\n.litertlm", ByteArrayInputStream(byteArrayOf(1)))
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun rejectsArbitraryFileWithLitertlmExtensionWithoutKeepingPartialFile() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        assertEquals("UNSUPPORTED_MODEL_FORMAT", assertThrows(IllegalArgumentException::class.java) {
            store.importModel("fake.litertlm", ByteArrayInputStream(ByteArray(512)))
        }.message)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun rejectsEmptyTruncatedOversizedAndSizeMismatchedImportsWithoutLeavingParts() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        assertEquals("EMPTY_MODEL", assertThrows(IllegalArgumentException::class.java) {
            store.importModel("empty.litertlm", ByteArrayInputStream(byteArrayOf()))
        }.message)
        assertEquals("CORRUPT_MODEL_CONTAINER", assertThrows(IllegalArgumentException::class.java) {
            store.importModel("truncated.litertlm", ByteArrayInputStream("LITERTLM".toByteArray()))
        }.message)
        assertEquals("MODEL_TOO_LARGE", assertThrows(IllegalArgumentException::class.java) {
            store.importModel("large.litertlm", ByteArrayInputStream(byteArrayOf(1)), LocalAIModelStore.MAX_MODEL_BYTES + 1)
        }.message)
        assertEquals("MODEL_SIZE_MISMATCH", assertThrows(IllegalArgumentException::class.java) {
            store.importModel("mismatch.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()), 1L)
        }.message)
        assertTrue(store.listModels().isEmpty())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun failedCopyRemovesPartialWeightFile() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val broken = object : InputStream() {
            private var sent = false
            override fun read(): Int {
                if (!sent) { sent = true; return 'L'.code }
                throw java.io.IOException("provider interrupted")
            }
        }
        assertThrows(java.io.IOException::class.java) { store.importModel("model.litertlm", broken) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun fatalSourceFailureStillRemovesPartialImportFile() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val failing = object : InputStream() {
            private var sent = false
            override fun read(): Int {
                if (!sent) { sent = true; return 'L'.code }
                throw AssertionError("simulated fatal source failure")
            }
        }
        assertThrows(AssertionError::class.java) { store.importModel("interrupted.litertlm", failing) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun startupCleanupRemovesInterruptedEncryptedImportsAndStalePlaintextStage() {
        val root = File(temporaryFolder.root, "models").apply { mkdirs() }
        val partial = File(root, "import-${"a".repeat(32)}.nxm.part").apply { writeText("partial") }
        val staging = File(root.parentFile, "${root.name}-plaintext-stage").apply { mkdirs() }
        val stalePlaintext = File(staging, "${"b".repeat(64)}-stale.litertlm").apply { writeText("plain") }
        val unrelated = File(root, "keep.txt").apply { writeText("keep") }
        val store = newTestLocalAIStore(root)
        assertEquals(2, store.cleanupIncompleteImports())
        assertFalse(partial.exists())
        assertFalse(stalePlaintext.exists())
        assertTrue(unrelated.isFile)
    }

    @Test fun cancelledSafPickerDoesNotCreateModelFiles() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        assertNull(store.importPickedModel(null, null))
        assertFalse(root.exists())
        assertNull(store.importPickedModel("model.litertlm", null))
        assertFalse(root.exists())
    }

    @Test fun pickedStreamClosesWhenPreflightRejectsTheFilename() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        var closed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.importPickedModel("unsupported.bin", source)
        }
        assertTrue(closed)
        assertFalse(root.exists())
    }

    @Test fun modelStatePersistsAcrossStoreInstancesAndCanBeDeleted() {
        val root = File(temporaryFolder.root, "models")
        val firstStore = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val imported = firstStore.importModel("local-test.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        val restartedStore = newTestLocalAIStore(root) { Long.MAX_VALUE }
        assertEquals(imported.id, restartedStore.listModels().single().id)
        assertEquals("local-test.litertlm", restartedStore.findModel(imported.id)?.displayName)
        assertTrue(restartedStore.deleteModel(imported.id))
        assertTrue(restartedStore.listModels().isEmpty())
        assertFalse(restartedStore.deleteModel("../../unsafe"))
    }

    @Test fun legacyPlaintextModelMigratesOnlyAfterEncryptedCommit() {
        val root = File(temporaryFolder.root, "models").apply { mkdirs() }
        val bytes = LiteRtLmContainerFixture.bytes()
        val id = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val legacy = File(root, "$id.litertlm").apply { writeBytes(bytes) }
        val oldLabel = File(root, "$id.name").apply { writeText("legacy-name.litertlm") }
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }

        assertTrue(store.hasLegacyPlaintextModels())
        val result = store.migrateLegacyPlaintextModels()

        assertEquals(1, result.migrated)
        assertEquals(0, result.remainingPlaintextFiles)
        assertFalse(legacy.exists())
        assertFalse(oldLabel.exists())
        assertTrue(File(root, "$id.nxm").isFile)
        assertEquals("legacy-name.litertlm", store.findModel(id)?.displayName)
    }

    @Test fun invalidLegacyFilenameHashIsPreservedAndNeverImported() {
        val root = File(temporaryFolder.root, "models").apply { mkdirs() }
        val legacy = File(root, "${"a".repeat(64)}.litertlm").apply { writeBytes(LiteRtLmContainerFixture.bytes()) }
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }

        val result = store.migrateLegacyPlaintextModels()

        assertEquals(0, result.migrated)
        assertEquals(1, result.remainingPlaintextFiles)
        assertTrue(legacy.isFile)
        assertTrue(store.listModels().isEmpty())
    }

    @Test fun corruptedEncryptedMetadataFallsBackToSafeNonAuthoritativeLabel() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val imported = store.importModel("safe-name.litertlm", ByteArrayInputStream(LiteRtLmContainerFixture.bytes()))
        java.io.RandomAccessFile(imported.file, "rw").use { file ->
            file.seek(file.length() - 9L)
            val original = file.readByte()
            file.seek(file.length() - 9L)
            file.writeByte(original.toInt() xor 0x01)
        }
        val listed = store.findModel(imported.id)!!
        assertEquals("Imported model ${imported.id.take(12)}", listed.displayName)
        assertEquals(root.canonicalFile, listed.file.canonicalFile.parentFile)
        assertTrue(listed.file.isFile)
        assertEquals("MODEL_METADATA_AUTHENTICATION_FAILED", listed.inspectionError)
    }

    @Test fun unknownArchitectureImportRemainsRuntimeLoadRequired() {
        val root = File(temporaryFolder.root, "models")
        val store = newTestLocalAIStore(root) { Long.MAX_VALUE }
        val imported = store.importModel(
            "unknown-architecture.litertlm",
            ByteArrayInputStream(LiteRtLmContainerFixture.bytes(architecture = "future_decoder_v9"))
        )
        assertEquals("future_decoder_v9", imported.inspection?.architecture)
        assertEquals("STRUCTURE_OK_RUNTIME_LOAD_REQUIRED", imported.inspection?.compatibility)
        assertTrue(store.findModel(imported.id)?.file?.isFile == true)
    }
}
