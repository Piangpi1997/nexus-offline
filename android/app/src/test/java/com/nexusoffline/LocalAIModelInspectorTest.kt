package com.nexusoffline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalAIModelInspectorTest {
    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    @Test fun inspectsSupportedContainerVersionAndDeclaredSections() {
        val file = LiteRtLmContainerFixture.write(File(temporaryFolder.root, "sample.litertlm"))
        val inspection = LocalAIModelInspector().inspect(file)
        assertEquals("LiteRT-LM container", inspection.format)
        assertEquals("1.0.0", inspection.formatVersion)
        assertTrue(inspection.structurallyCompatible)
        assertEquals("STRUCTURE_OK_RUNTIME_LOAD_REQUIRED", inspection.compatibility)
        assertEquals(listOf("LLM metadata", "SentencePiece tokenizer", "TFLite model"), inspection.sectionTypes)
        assertTrue(inspection.estimatedRamBytes >= file.length())
    }

    @Test fun rejectsWrongMagicEvenWhenExtensionLooksValid() {
        val file = File(temporaryFolder.root, "fake.litertlm").apply { writeBytes(ByteArray(128) { it.toByte() }) }
        val error = assertThrows(IllegalArgumentException::class.java) { LocalAIModelInspector().inspect(file) }
        assertEquals("UNSUPPORTED_MODEL_FORMAT", error.message)
    }

    @Test fun rejectsUnsupportedContainerMajorVersion() {
        val file = LiteRtLmContainerFixture.write(File(temporaryFolder.root, "future.litertlm"), majorVersion = 2)
        val error = assertThrows(IllegalArgumentException::class.java) { LocalAIModelInspector().inspect(file) }
        assertEquals("UNSUPPORTED_MODEL_VERSION", error.message)
    }

    @Test fun unknownArchitectureIsNotClaimedAsRuntimeCompatible() {
        val file = LiteRtLmContainerFixture.write(
            File(temporaryFolder.root, "future-architecture.litertlm"),
            architecture = "future_decoder_v9"
        )
        val inspection = LocalAIModelInspector().inspect(file)
        assertEquals("future_decoder_v9", inspection.architecture)
        assertEquals("STRUCTURE_OK_RUNTIME_LOAD_REQUIRED", inspection.compatibility)
        assertTrue(inspection.structurallyCompatible)
    }

    @Test fun rejectsMissingTfliteOrTokenizerSections() {
        val noModel = LiteRtLmContainerFixture.write(File(temporaryFolder.root, "no-model.litertlm"), sectionTypes = intArrayOf(1, 4, 5))
        assertEquals("UNSUPPORTED_MODEL_CONTENT", assertThrows(IllegalArgumentException::class.java) {
            LocalAIModelInspector().inspect(noModel)
        }.message)

        val noTokenizer = LiteRtLmContainerFixture.write(File(temporaryFolder.root, "no-tokenizer.litertlm"), sectionTypes = intArrayOf(3, 1, 5))
        assertEquals("UNSUPPORTED_MODEL_CONTENT", assertThrows(IllegalArgumentException::class.java) {
            LocalAIModelInspector().inspect(noTokenizer)
        }.message)
    }

    @Test fun rejectsBadTfliteIdentifierAndTruncatedHeader() {
        val badTflite = LiteRtLmContainerFixture.write(File(temporaryFolder.root, "bad-tflite.litertlm"))
        badTflite.seekForTestCorruption()
        assertEquals("INVALID_TFLITE_MODEL_SECTION", assertThrows(IllegalArgumentException::class.java) {
            LocalAIModelInspector().inspect(badTflite)
        }.message)

        val truncated = File(temporaryFolder.root, "truncated.litertlm").apply { writeBytes("LITERTLM".toByteArray()) }
        assertEquals("CORRUPT_MODEL_CONTAINER", assertThrows(IllegalArgumentException::class.java) {
            LocalAIModelInspector().inspect(truncated)
        }.message)
    }

    private fun File.seekForTestCorruption() {
        val bytes = readBytes()
        // The first declared TFLite section is at byte 16 KiB; byte +4..+7 is TFL3.
        bytes[16_384 + 4] = 'X'.code.toByte()
        writeBytes(bytes)
    }
}
