package com.nexusoffline

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupProtocolTest {
    private val peers = (1..12).map { "peer-$it" }
    private fun envelope(messageId: String = UUID.randomUUID().toString(), members: List<String> = peers) = GroupEnvelope(
        groupId = "0123456789abcdef0123456789abcdef",
        messageId = messageId,
        originId = peers.first(), ownerId = peers.first(), senderId = peers.first(),
        membershipVersion = 1,
        memberIds = members,
        ttlRemaining = GroupProtocol.MAX_TTL,
        hopPath = listOf(peers.first())
    )

    @Test fun twelvePeerOneHundredMessageSimulationDeduplicatesAndIsolatesOfflinePeer() {
        val peerWindows = peers.associateWith { GroupReplayWindow() }
        val offlinePeer = "peer-12"
        var acceptedDeliveries = 0
        var duplicateDeliveries = 0
        repeat(100) { index ->
            val packet = envelope(UUID.nameUUIDFromBytes("msg-$index".toByteArray()).toString())
            val directTargets = GroupProtocol.forwardTargets(packet, peers.drop(1).filter { it != offlinePeer }, peers.first())
            assertEquals(10, directTargets.size)
            directTargets.forEach { receiver ->
                if (peerWindows.getValue(receiver).accept(packet.groupId, packet.messageId, index.toLong())) acceptedDeliveries++
                repeat(3) { if (!peerWindows.getValue(receiver).accept(packet.groupId, packet.messageId, index.toLong())) duplicateDeliveries++ }
            }
            assertTrue("offline receiver is isolated and queued for later retry", offlinePeer !in directTargets)
            assertTrue("reconnect retry finds missing receiver", offlinePeer in GroupProtocol.forwardTargets(packet, peers.drop(1), peers.first()))
        }
        assertEquals(1_000, acceptedDeliveries)
        assertEquals(3_000, duplicateDeliveries)
    }

    @Test fun validatesUniqueBoundedMembershipAndAllowsJoinLeaveVersionUpdates() {
        val original = envelope()
        assertNull(GroupProtocol.validate(original))
        val joined = original.copy(membershipVersion = 2, memberIds = peers + "peer-13")
        assertEquals("BAD_MEMBER_COUNT", GroupProtocol.validate(joined))
        val validJoin = original.copy(membershipVersion = 2, memberIds = peers.take(11) + "peer-13")
        assertNull(GroupProtocol.validate(validJoin))
        val leave = validJoin.copy(membershipVersion = 3, memberIds = validJoin.memberIds - "peer-5")
        assertNull(GroupProtocol.validate(leave))
        assertEquals("SENDER_NOT_MEMBER", GroupProtocol.validate(leave.copy(senderId = "peer-5")))
    }

    @Test fun uniqueMessageIdsAndReplayWindowRejectDuplicateAcrossRelays() {
        val cache = GroupReplayWindow()
        val packet = envelope()
        assertTrue(cache.accept(packet.groupId, packet.messageId, 1_000L))
        assertFalse(cache.accept(packet.groupId, packet.messageId, 1_001L))
        assertTrue(cache.accept("fedcba9876543210fedcba9876543210", packet.messageId, 1_001L))
        assertEquals(2, cache.size(1_002L))
    }

    @Test fun ttlAndHopPathPreventLoopsAndUnknownRelays() {
        val origin = envelope()
        val atPeer2 = GroupProtocol.relay(origin, "peer-2")
        assertNotNull(atPeer2)
        val atPeer3 = GroupProtocol.relay(atPeer2!!, "peer-3")
        assertNotNull(atPeer3)
        assertEquals(listOf("peer-1", "peer-2", "peer-3"), atPeer3!!.hopPath)
        assertNull(GroupProtocol.relay(atPeer3, "peer-2"))
        assertEquals("HOP_LOOP_OR_UNKNOWN_PEER", GroupProtocol.validate(atPeer3.copy(hopPath = listOf("peer-1", "peer-2", "peer-2", "peer-3"))))
        assertNull(GroupProtocol.relay(origin.copy(ttlRemaining = 0), "peer-2"))
    }

    @Test fun senderExclusionAndForwardingOnlyReachUnvisitedGroupMembers() {
        val packet = envelope().copy(hopPath = listOf("peer-1", "peer-2"), senderId = "peer-2")
        assertEquals(listOf("peer-4", "peer-3"), GroupProtocol.forwardTargets(packet, listOf("peer-2", "peer-4", "peer-1", "peer-3", "outsider", "peer-4"), "peer-6"))
    }

    @Test fun malformedGroupMessageIdMemberIdAndGroupIdAreRejected() {
        val packet = envelope()
        assertEquals("BAD_GROUP_ID", GroupProtocol.validate(packet.copy(groupId = "../bad")))
        assertEquals("BAD_MESSAGE_ID", GroupProtocol.validate(packet.copy(messageId = "message")))
        assertEquals("BAD_MEMBER_ID", GroupProtocol.validate(packet.copy(memberIds = peers.dropLast(1) + "../bad")))
        assertEquals("BAD_MEMBER_LIST", GroupProtocol.validate(packet.copy(memberIds = peers.dropLast(1) + "peer-1")))
    }

    @Test fun replayEntriesExpireAndCacheRemainsBounded() {
        val cache = GroupReplayWindow(capacity = 2, retentionMillis = 10)
        assertTrue(cache.accept("group", "m1", 1))
        assertTrue(cache.accept("group", "m2", 1))
        assertTrue(cache.accept("group", "m3", 1))
        assertEquals(2, cache.size(1))
        assertFalse(cache.accept("group", "m2", 2))
        assertTrue(cache.accept("group", "m2", 12))
    }

    @Test fun originAndCurrentSenderMustMatchHopEndpoints() {
        val packet = envelope()
        assertEquals("HOP_IDENTITY_MISMATCH", GroupProtocol.validate(packet.copy(senderId = "peer-2")))
        assertEquals("HOP_IDENTITY_MISMATCH", GroupProtocol.validate(packet.copy(originId = "peer-2")))
    }
}
