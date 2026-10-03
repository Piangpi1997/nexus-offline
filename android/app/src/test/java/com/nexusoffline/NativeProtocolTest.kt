package com.nexusoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProtocolTest {
    @Test fun payloadBoundaryIsEnforced() {
        assertFalse(NativeProtocol.acceptsPayloadSize(0))
        assertTrue(NativeProtocol.acceptsPayloadSize(1))
        assertTrue(NativeProtocol.acceptsPayloadSize(NativeProtocol.MAX_DATA_BYTES))
        assertFalse(NativeProtocol.acceptsPayloadSize(NativeProtocol.MAX_DATA_BYTES + 1))
    }

    @Test fun routingDeduplicatesAndExcludesSender() {
        val peers = (1..12).map { "peer-$it" } + listOf("peer-2", "")
        val targets = NativeProtocol.routeTargets(peers, "peer-1")
        assertEquals(11, targets.size)
        assertFalse(targets.contains("peer-1"))
        assertEquals(1, targets.count { it == "peer-2" })
    }

    @Test fun fileSizeAndEncryptedPayloadLimitsAreEnforced() {
        assertFalse(NativeProtocol.acceptsFileSize(0))
        assertTrue(NativeProtocol.acceptsFileSize(NativeProtocol.MAX_FILE_BYTES))
        assertFalse(NativeProtocol.acceptsFileSize(NativeProtocol.MAX_FILE_BYTES + 1))
        assertFalse(NativeProtocol.acceptsEncryptedFileSize(16))
        assertTrue(NativeProtocol.acceptsEncryptedFileSize(NativeProtocol.MAX_FILE_BYTES + 16))
        assertFalse(NativeProtocol.acceptsEncryptedFileSize(NativeProtocol.MAX_FILE_BYTES + 17))
    }

    @Test fun filenameIsReducedToSafeLeafName() {
        assertEquals("secret_.pdf", NativeProtocol.sanitizeFilename("../folder\\secret?.pdf"))
        assertEquals("file", NativeProtocol.sanitizeFilename("../../..."))
        assertTrue(NativeProtocol.sanitizeFilename("x".repeat(200)).length <= 160)
    }

    @Test fun transferIdsAreFixedLengthHexOnly() {
        assertTrue(NativeProtocol.isTransferId("0123456789abcdef0123456789ABCDEF"))
        assertFalse(NativeProtocol.isTransferId("../../secret"))
        assertFalse(NativeProtocol.isTransferId("abcd"))
    }

    @Test fun bridgeAllowsOnlyHandshakeOrEncryptedWireFrames() {
        assertTrue(NativeProtocol.acceptsWireType("hello"))
        assertTrue(NativeProtocol.acceptsWireType("ciphertext-v1"))
        assertFalse(NativeProtocol.acceptsWireType("message"))
        assertFalse(NativeProtocol.acceptsWireType(null))
    }
}
