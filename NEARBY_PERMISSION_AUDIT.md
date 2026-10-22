# Permission audit

Source `requiredPermissions()` requests Bluetooth scan/connect/advertise for API 31+, coarse location for <=28, coarse/fine location for API 29–31, and NEARBY_WIFI_DEVICES for API 33+. The manifest caps ACCESS_WIFI_STATE and CHANGE_WIFI_STATE at API 31. These declarations need validation against the resolved Nearby SDK and merged manifest before changes; no speculative manifest modifications made.

**MERGED MANIFEST VERIFICATION PENDING**.
