package com.nexusoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingQrTest {
    @Test fun payloadHasVersionPublicIdentityNonceAndExpiryButNoKeys() {
        val now = 1_800_000_000_000L
        val payload = PairingQr.create("NX-public_ref-1234", now)
        val json = PairingQr.encode(payload)
        assertEquals(PairingQr.PROTOCOL_VERSION, payload.protocolVersion)
        assertEquals(now + PairingQr.TTL_MILLIS, payload.expiresAtMillis)
        assertEquals(32, payload.nonce.length)
        assertTrue(payload.nonce.matches(Regex("^[A-Za-z0-9_-]{32}$")))
        assertTrue(json.contains("\"identityRef\":\"NX-public_ref-1234\""))
        assertTrue(json.contains("\"endpointData\":\"${PairingQr.BOOTSTRAP_SERVICE_ID}\""))
        assertFalse(json.contains("privateKey"))
        assertFalse(json.contains("sessionKey"))
        assertNull(PairingQr.validate(payload, now, emptySet()))
    }

    @Test fun expiredAndReplayPayloadsAreRejected() {
        val now = 1_800_000_000_000L
        val payload = PairingQr.create("NX-public_ref-1234", now)
        assertEquals("QR_EXPIRED", PairingQr.validate(payload, payload.expiresAtMillis, emptySet()))
        val used = mutableSetOf<String>()
        assertNull(PairingQr.consume(payload, now, used))
        assertEquals("QR_REPLAYED", PairingQr.consume(payload, now, used))
    }

    @Test fun malformedOrLongLivedPayloadsAreRejected() {
        val now = 1_800_000_000_000L
        val payload = PairingQr.create("NX-public_ref-1234", now)
        assertEquals("BAD_IDENTITY_REF", PairingQr.validate(payload.copy(identityRef = "../private"), now, emptySet()))
        assertEquals("BAD_NONCE", PairingQr.validate(payload.copy(nonce = "short"), now, emptySet()))
        assertEquals("QR_EXPIRY_INVALID", PairingQr.validate(payload.copy(expiresAtMillis = now + PairingQr.TTL_MILLIS + 30_000L), now, emptySet()))
    }

    @Test fun wrongBootstrapEndpointIsRejected() {
        val payload = PairingQr.create("NX-public_ref-1234", 1_800_000_000_000L)
            .copy(endpointData = "other.nearby.service")
        assertEquals("ENDPOINT_MISMATCH", PairingQr.validate(payload, 1_800_000_000_000L, emptySet()))
    }

    @Test fun decoderRequiresExactFieldsAndRejectsMalformedQr() {
        val payload = PairingQr.create("NX-public_ref-1234", 1_800_000_000_000L)
        val json = PairingQr.encode(payload)
        assertEquals(payload, PairingQr.decode(json))
        assertTrue(PairingQr.decode("not-json") == null)
        assertTrue(PairingQr.decode(json.dropLast(1) + ",\"privateKey\":\"x\"}") == null)
        assertTrue(PairingQr.decode(" ".repeat(2049)) == null)
    }
}
