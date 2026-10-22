# Nearby P0 source findings

Verified: `emitFailure()` previously discarded `ApiException.statusCode`; advertising and discovery booleans became true only on asynchronous success, allowing duplicate in-flight calls. The branch adds a native numeric status payload and independent start-state/token guards. Root cause of physical radio failure remains **unknown** until device diagnostics are captured.

**PRODUCTION VERIFICATION PENDING**.
