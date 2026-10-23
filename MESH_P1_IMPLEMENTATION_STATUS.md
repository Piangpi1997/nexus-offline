# Mesh P1 implementation status

Branch: p0-nearby-fix. Main untouched.

Implemented:
- android/app/src/main/assets/mesh-protocol.js: isolated UMD module for bounded version-1 envelopes, expiry, hop budget and in-memory duplicate suppression.
- tests/mesh-protocol.test.cjs: 14 Node built-in test cases.

Not implemented:
- Loading the module in the production Android WebView.
- Peer identity binding, cryptographic signature verification, end-to-end encryption.
- Durable SQLite store-and-forward queue, transport integration, background relay.
- Multi-device tests and actual city mesh coverage.

Security warning: ciphertext and signature fields are only shape-checked. They are NOT authenticated. The module MUST NOT be used to forward untrusted live messages until cryptographic verification and durable replay protection are implemented.

To run on a machine with Node 18+: node --test tests/mesh-protocol.test.cjs

Test execution in GitHub Actions and on Android is not yet verified. The existing Nearby file-transfer failure remains an independent P0 issue. Do not merge into main until tests and physical-device validation pass.
