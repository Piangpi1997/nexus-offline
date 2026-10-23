# NEXUS OFFLINE — City Mesh Architecture and Implementation Plan

Status: DESIGN / NOT IMPLEMENTED. Branch: p0-nearby-fix. No changes to main.

## Constraints
- Google Nearby Connections is a single-hop transport, not a city-scale mesh.
- Android phones alone cannot guarantee citywide coverage or unattended background relay.
- Delivery requires a time-respecting chain of contacts or deployed relay/backhaul infrastructure.
- Preserve existing encrypted local data, QR pairing, native Nearby bridge, and file-transfer functionality.

## Architecture
1. TransportAdapter: existing Nearby connection manager; future dedicated Wi-Fi/backhaul adapters behind one interface.
2. PeerIdentity: persistent signing key, stable peer ID, QR verified fingerprints; rotate ephemeral transport identifiers.
3. MeshEnvelope: protocolVersion, messageId, senderId, recipientId, createdAt, expiresAt, hopLimit, ciphertext, signature, payloadType; canonical encoding and size bounds.
4. DurableOutbox: SQLite journal for queued/inflight/acknowledged/expired messages; transactional dedup by messageId.
5. Router: opportunistic store-carry-forward with hop budget and expiry; initially controlled flooding with per-peer seen sets and quotas; later delivery-probability routing.
6. RelayPolicy: explicit opt-in; foreground service where platform permits; battery/storage quotas, per-peer rate limits, abuse controls.
7. EndToEndCrypto: encrypt to destination key; intermediate relays never receive plaintext or decryption keys. Existing Nearby session encryption is only hop-to-hop.
8. ReceiptManager: signed end-recipient receipts distinct from hop acknowledgements; do not display Delivered on relay receipt.
9. Observability: queued, forwarded, destination-acknowledged, expired; diagnostic status codes; avoid logging message contents.
10. Infrastructure: optional mains-powered Android relay nodes and licensed/legal point-to-point backhaul; measure connectivity before coverage claims.

## MVP wire protocol
Envelope v1 must be length-bounded and authenticated; reject invalid version, TTL, hop count, signature, recipient format, and replay. Never trust remote claimed peer ID without binding to cryptographic identity.
- HELLO: supported versions, ephemeral session ID, capabilities, compact inventory summary.
- OFFER: bounded list of message IDs available for recipient/relay.
- REQUEST: bounded list of requested message IDs.
- DATA: encrypted envelope chunks with checksum and transfer IDs.
- HOP_ACK: custody acknowledgement only.
- DELIVERY_RECEIPT: signed by recipient, end-to-end verified.
- CANCEL / ERROR: explicit reasons, bounded retries.

## Phased delivery
P0: Keep Nearby discovery/connect and one-hop encrypted file transfer working; capture native status and file-transfer failures; two-device regression.
P1: Define MeshEnvelope and transport-neutral interfaces; unit tests for TTL, hop limit, dedup, replay, size bounds.
P2: Durable queue, opt-in forwarding, per-peer inventory sync, quotas, crash recovery; three-device A-B-C relay tests.
P3: E2EE identity/key lifecycle, verified delivery receipts, compromised relay tests, denial-of-service controls.
P4: Field pilot (5–20 devices) with measurable delivery rate, latency, battery consumption and coverage gaps.
P5: Fixed relay/backhaul deployment planning, maintenance, legal/regulatory review, scale testing.

## Acceptance gates
- A->B->C message reaches C with B unable to decrypt it; no direct A-C link.
- Offline destination receives message after contact resumes.
- Duplicate/replayed/expired messages are rejected.
- App restart retains queued messages; cancellation and quotas enforced.
- Failed file transfer does not silently report success.
- Real-device verification is pending; no citywide performance claims.

## Non-goals for first release
No guaranteed real-time citywide connectivity, no emergency-service availability claims, no anonymous unrestricted public relay, no automatic background relay without user consent.
