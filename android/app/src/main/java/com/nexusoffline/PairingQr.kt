package com.nexusoffline

import java.security.SecureRandom
import org.json.JSONObject

internal data class PairingQrPayload(
    val protocolVersion: Int,
    val identityRef: String,
    val nonce: String,
    val expiresAtMillis: Long,
    val endpointData: String = PairingQr.BOOTSTRAP_SERVICE_ID
)

internal data class PairingQrScanResult(
    val payload: PairingQrPayload? = null,
    val errorCode: String? = null
) {
    val ok: Boolean get() = payload != null && errorCode == null
}

internal object PairingQr {
    const val PROTOCOL_VERSION = 1
    const val TTL_MILLIS = 120_000L
    const val BOOTSTRAP_SERVICE_ID = "com.nexusoffline.nearby.v1"
    private const val CLOCK_SKEW_MILLIS = 15_000L
    private val random = SecureRandom()

    fun create(identityRef: String, nowMillis: Long = System.currentTimeMillis()): PairingQrPayload {
        require(identityRef.matches(Regex("^[A-Za-z0-9_-]{2,64}$"))) { "BAD_IDENTITY_REF" }
        val nonce = ByteArray(24).also(random::nextBytes)
        return PairingQrPayload(
            protocolVersion = PROTOCOL_VERSION,
            identityRef = identityRef,
            nonce = encodeBase64Url(nonce),
            expiresAtMillis = nowMillis + TTL_MILLIS,
            endpointData = BOOTSTRAP_SERVICE_ID
        ).also { nonce.fill(0) }
    }

    fun encode(payload: PairingQrPayload): String {
        require(validate(payload, payload.expiresAtMillis - TTL_MILLIS, emptySet()) == null) { "BAD_PAIRING_QR" }
        val endpoint = "\"${escape(payload.endpointData)}\""
        return "{\"protocolVersion\":${payload.protocolVersion},\"identityRef\":\"${escape(payload.identityRef)}\",\"nonce\":\"${escape(payload.nonce)}\",\"expiresAt\":${payload.expiresAtMillis},\"endpointData\":$endpoint}"
    }

    fun decode(raw: String): PairingQrPayload? {
        if (raw.length !in 2..2048) return null
        return runCatching {
            val value = JSONObject(raw)
            val allowed = setOf("protocolVersion", "identityRef", "nonce", "expiresAt", "endpointData")
            require(value.length() == allowed.size && allowed.all(value::has)) { "BAD_QR_SCHEMA" }
            require(value.opt("protocolVersion") is Number) { "BAD_QR_VERSION" }
            require(value.opt("expiresAt") is Number) { "BAD_QR_EXPIRY" }
            require(value.opt("identityRef") is String && value.opt("nonce") is String && value.opt("endpointData") is String) { "BAD_QR_FIELDS" }
            PairingQrPayload(
                protocolVersion = value.getInt("protocolVersion"),
                identityRef = value.getString("identityRef"),
                nonce = value.getString("nonce"),
                expiresAtMillis = value.getLong("expiresAt"),
                endpointData = value.getString("endpointData")
            )
        }.getOrNull()
    }

    /** Validates scanner output without mutating the caller's nonce set or exposing QR contents. */
    fun scan(raw: String?, nowMillis: Long, usedNonces: Set<String>): PairingQrScanResult {
        if (raw == null) return PairingQrScanResult(errorCode = "QR_SCAN_CANCELLED")
        val payload = decode(raw) ?: return PairingQrScanResult(errorCode = "QR_FORMAT_INVALID")
        val issue = validate(payload, nowMillis, usedNonces)
        return if (issue == null) PairingQrScanResult(payload = payload)
        else PairingQrScanResult(errorCode = issue)
    }

    /** Returns null only for a currently valid, unused bootstrap payload. */
    fun validate(payload: PairingQrPayload, nowMillis: Long, usedNonces: Set<String>): String? {
        if (payload.protocolVersion != PROTOCOL_VERSION) return "UNSUPPORTED_VERSION"
        if (!payload.identityRef.matches(Regex("^[A-Za-z0-9_-]{2,64}$"))) return "BAD_IDENTITY_REF"
        if (!payload.nonce.matches(Regex("^[A-Za-z0-9_-]{32}$"))) return "BAD_NONCE"
        if (payload.expiresAtMillis <= nowMillis) return "QR_EXPIRED"
        if (payload.expiresAtMillis > nowMillis + TTL_MILLIS + CLOCK_SKEW_MILLIS) return "QR_EXPIRY_INVALID"
        if (payload.endpointData != BOOTSTRAP_SERVICE_ID) return "ENDPOINT_MISMATCH"
        if (payload.nonce in usedNonces) return "QR_REPLAYED"
        return null
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val output = StringBuilder((bytes.size * 4 + 2) / 3)
        var offset = 0
        while (offset < bytes.size) {
            val remaining = bytes.size - offset
            val block = ((bytes[offset].toInt() and 0xff) shl 16) or
                (if (remaining > 1) (bytes[offset + 1].toInt() and 0xff) shl 8 else 0) or
                (if (remaining > 2) (bytes[offset + 2].toInt() and 0xff) else 0)
            output.append(alphabet[(block ushr 18) and 63])
            output.append(alphabet[(block ushr 12) and 63])
            if (remaining > 1) output.append(alphabet[(block ushr 6) and 63])
            if (remaining > 2) output.append(alphabet[block and 63])
            offset += 3
        }
        return output.toString()
    }
}
