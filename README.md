# CloudShelf

**CloudShelf** is a personal-first media ingestion, archival, staging, deduplication, and synchronization utility for Android (primary device: Google Pixel).

CloudShelf solves a specific problem: managing large volumes of personal photos and videos across multiple Google accounts, Takeout archives, and secondary devices using a Pixel phone as a temporary, controlled staging device with strict data safety guarantees.

---

## Core Product Cycle

```
SOURCE (Takeout / Companion Phone / Folder / Google Photos)
   ↓
IMPORT
   ↓
PIXEL STAGING STORAGE (Configurable limit: 500 MB – 20 GB, default 3 GB)
   ↓
GOOGLE PHOTOS BACKUP
   ↓
VERIFY BACKUP ("Confirmed by you")
   ↓
DELETE LOCAL PIXEL STAGING COPY
   ↓
FREE PIXEL STORAGE
   ↓
NEXT BATCH
   ↓
REPEAT UNTIL COMPLETE
```

---

## Key Principles & Invariants

1. **Absolute Data Safety & Zero AI-Slop:**
   * Clean, native Android settings/utility layout using Material 3. Zero marketing copy, zero fake graphs, zero decorative fluff.
2. **Golden Safety Rule:**
   * A local staging file is **never** deleted until backup is confirmed by the user.
   * Local deletion removes **only** CloudShelf staging copies from the Pixel.
   * Source originals, source folders, Takeout archives, and Google Photos cloud media are **never** deleted.
3. **Automatic Device-to-Pixel Sync (Dual-Role Architecture):**
   * **Pixel Receiver:** Listens on local Wi-Fi, advertises via mDNS (`_cloudshelf._tcp`), calculates safe storage headroom, and enforces backpressure.
   * **Source Companion:** Observes Android `MediaStore` for new photos/videos, queries Pixel capacity, and streams in 2 MB chunks with automatic offset resumption.
   * **Invariant:** Companion phone permanently retains all source originals.
4. **Google API Compliance:**
   * Official Google Photos Picker API (`photospicker.googleapis.com`) with session lifecycle management.
   * Bulk archives handled via streaming Google Takeout scanner with Zip-Slip path traversal rejection.
5. **Layered Deduplication:**
   * Level 1 (Source ID) $\to$ Level 2 (Size/MIME) $\to$ Level 3 (Filename/Metadata) $\to$ Level 4 (Streaming SHA-256 via 64 KB buffers).
   * Retains provenance across accounts without wasting physical storage.
6. **Dynamic Storage Guard:**
   * Calculates real `StatFs` metrics against a user-configurable minimum free-space reserve (default: 5 GB). Automatically pauses before storage exhaustion.

---

## Tech Stack

* **Language:** Kotlin 2.2 / 2.3
* **UI:** Jetpack Compose, Material 3, Navigation 3
* **Database:** SQLite WAL mode with indexed entities (`CloudShelfDb`)
* **Storage:** Android Scoped Storage (`MediaStore`), Storage Access Framework (SAF)
* **Networking & Discovery:** Android Network Service Discovery (mDNS/NSD), lightweight local HTTP sync daemon
* **Build System:** Gradle 9.1.0, Java 25 LTS, Android SDK 35/36

---

## Building & Testing

### Prerequisites
* Android SDK 35 or 36
* Java 17, 21, or 25

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Run Test Suite
```bash
./gradlew test
```
Includes:
* `SafeFileIOAndHashingTest`: streaming SHA-256, Zip-Slip sandbox traversal rejection, collision resolution, atomic writes.
* `CriticalDataSafetySimulationTest`: 100 GB source simulation over 3 GB batches, Golden Safety Rule deletion locks, oversized file isolation.
* `DeviceSyncSubsystemTest`: pairing PIN generation, backpressure capacity limits, 47% chunk resumption, corruption rejection, source original permanence.

---

## License

Personal-first private media utility. All rights reserved.
