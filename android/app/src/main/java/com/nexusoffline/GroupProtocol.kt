package com.nexusoffline

import java.util.LinkedHashMap

internal data class GroupEnvelope(
    val groupId: String,
    val messageId: String,
    val originId: String,
    val ownerId: String,
    val senderId: String,
    val membershipVersion: Long,
    val memberIds: List<String>,
    val ttlRemaining: Int,
    val hopPath: List<String>
)

/** Protocol-side checks for encrypted group envelopes; not a claim of Nearby topology capacity. */
internal object GroupProtocol {
    const val VERSION = 1
    const val MAX_MEMBERS = 12
    const val MAX_TTL = 8
    private val DEVICE_ID = Regex("^[-A-Za-z0-9_]{2,40}$")
    private val GROUP_ID = Regex("^[A-Fa-f0-9]{32}$")
    private val MESSAGE_ID = Regex("^[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[1-5][A-Fa-f0-9]{3}-[89ABab][A-Fa-f0-9]{3}-[A-Fa-f0-9]{12}$")

    fun validate(envelope: GroupEnvelope): String? {
        if (!GROUP_ID.matches(envelope.groupId)) return "BAD_GROUP_ID"
        if (!MESSAGE_ID.matches(envelope.messageId)) return "BAD_MESSAGE_ID"
        if (!DEVICE_ID.matches(envelope.ownerId) || !DEVICE_ID.matches(envelope.originId) || !DEVICE_ID.matches(envelope.senderId)) return "BAD_MEMBER_ID"
        if (envelope.membershipVersion < 0L) return "BAD_MEMBERSHIP_VERSION"
        if (envelope.memberIds.isEmpty() || envelope.memberIds.size > MAX_MEMBERS) return "BAD_MEMBER_COUNT"
        if (envelope.memberIds.any { !DEVICE_ID.matches(it) }) return "BAD_MEMBER_ID"
        if (envelope.memberIds.distinct().size != envelope.memberIds.size) return "BAD_MEMBER_LIST"
        if (envelope.ownerId !in envelope.memberIds || envelope.originId !in envelope.memberIds || envelope.senderId !in envelope.memberIds) return "SENDER_NOT_MEMBER"
        if (envelope.ttlRemaining !in 0..MAX_TTL) return "BAD_TTL"
        if (envelope.hopPath.isEmpty() || envelope.hopPath.size > MAX_TTL + 1) return "BAD_HOP_PATH"
        if (envelope.hopPath.distinct().size != envelope.hopPath.size || envelope.hopPath.any { it !in envelope.memberIds }) return "HOP_LOOP_OR_UNKNOWN_PEER"
        if (envelope.hopPath.first() != envelope.originId || envelope.hopPath.last() != envelope.senderId) return "HOP_IDENTITY_MISMATCH"
        return null
    }

    fun forwardTargets(envelope: GroupEnvelope, connectedMembers: Iterable<String>, localId: String): List<String> {
        if (validate(envelope) != null || envelope.ttlRemaining <= 0 || localId !in envelope.memberIds || (localId in envelope.hopPath && localId != envelope.senderId)) return emptyList()
        return connectedMembers.asSequence()
            .filter { it in envelope.memberIds && it != localId && it != envelope.senderId && it !in envelope.hopPath }
            .distinct()
            .toList()
    }

    fun relay(envelope: GroupEnvelope, localId: String): GroupEnvelope? {
        if (validate(envelope) != null || envelope.ttlRemaining <= 0 || localId !in envelope.memberIds || localId in envelope.hopPath || envelope.hopPath.size >= MAX_TTL + 1) return null
        return envelope.copy(senderId = localId, ttlRemaining = envelope.ttlRemaining - 1, hopPath = envelope.hopPath + localId)
    }
}

/** Bounded replay window keyed by group and message ID; entries expire monotonically. */
internal class GroupReplayWindow(
    private val capacity: Int = 4096,
    private val retentionMillis: Long = 24L * 60L * 60L * 1000L
) {
    private val entries = LinkedHashMap<String, Long>()

    @Synchronized
    fun accept(groupId: String, messageId: String, nowMillis: Long): Boolean {
        prune(nowMillis)
        val key = "$groupId|$messageId"
        if (key in entries) return false
        entries[key] = nowMillis + retentionMillis
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return true
    }

    @Synchronized
    fun size(nowMillis: Long): Int {
        prune(nowMillis)
        return entries.size
    }

    private fun prune(nowMillis: Long) {
        entries.entries.removeAll { it.value <= nowMillis }
    }
}
