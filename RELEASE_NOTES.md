# CloudShelf v1.0.0 — Production Release

CloudShelf is a personal-first media ingestion, archival, staging, deduplication, and automatic device-to-Pixel physical synchronization utility for Android.

### 🌟 Key Highlights

- **Automatic Device-to-Pixel Physical Sync Subsystem**:
  - Zero-touch peer discovery via Android Network Service Discovery (mDNS / NSD, `_cloudshelf._tcp`).
  - Resumable 2 MB binary chunked streaming transport with streaming SHA-256 verification.
  - Backpressure & staging capacity negotiation (receiver informs source of available bytes before admitting transfers).
  - Multi-tier device identity and pairing authorization (PIN verification, device role assignment: `PIXEL_RECEIVER` vs `SOURCE_TRANSMITTER`).
  - **Source-Safe Invariant**: Source physical originals are permanently retained; only Pixel staging buffers are managed.

- **StorageGuard & Staging Lifecycle Engine**:
  - Controlled batch staging (default 3 GB chunks, configurable).
  - Dynamic disk reserve safety enforcement (guarantees ≥ 2 GB minimum free internal storage).
  - Intentional fallback mechanisms: Wi-Fi only transfers with battery/thermal throttling awareness.

- **Golden Safety Rule & Double-Key Deletion**:
  - Explicit confirmation workflows requiring batch ID, file count, total size, and target cloud destination confirmation before staging cleanup.
  - Pixel staging buffer deletion only — cloud originals and source files are never deleted or modified.
  - Audit trail logging and exportable transfer receipts.

- **Layered Deduplication Engine**:
  - Tier 1: Fast metadata checks (exact size matching, EXIF capture timestamp).
  - Tier 2: 64 KB header/footer partial hashing.
  - Tier 3: Complete streaming SHA-256 validation.
  - Duplicate resolution policies: Skip, Overwrite, or Fork copy.

- **Google Ecosystem Ingestion**:
  - Official Google Photos Picker API integration (March 2025+ compliant session-based flow).
  - Google Takeout archive inspector: Safe streaming ZIP extraction with path traversal (Zip-Slip) defense and sidecar `.json` metadata preservation.

- **Design & UX**:
  - Clean, functional Android Settings / Google Files style layout built with Jetpack Compose & Material 3.
  - High information density with real-time transfer progress, batch inspector, and device pairing management.

---

### 📦 Asset
- **APK**: `app-release.apk` (Android 8.0+ / API 26+)
