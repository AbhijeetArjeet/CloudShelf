package com.example.cloudshelf.sync

import android.content.Context
import android.net.Uri
import com.example.cloudshelf.database.CloudShelfDb
import com.example.cloudshelf.deduplication.StreamingHasher
import com.example.cloudshelf.model.*
import com.example.cloudshelf.storage.MediaStoreBridge
import com.example.cloudshelf.storage.StorageGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.NetworkInterface

class DeviceSyncManager(
    private val context: Context,
    private val db: CloudShelfDb,
    private val storageGuard: StorageGuard,
    private val mediaStoreBridge: MediaStoreBridge
) {
    val discoveryManager = DeviceDiscoveryManager(context)
    val syncServer = SyncServer(context, db, storageGuard, mediaStoreBridge)
    val syncClient = SyncClient(db)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _currentPairing = MutableStateFlow<PairingPayload?>(null)
    val currentPairing: StateFlow<PairingPayload?> = _currentPairing.asStateFlow()

    private val _companionSyncState = MutableStateFlow("Idle")
    val companionSyncState: StateFlow<String> = _companionSyncState.asStateFlow()

    private val _waitingFilesCount = MutableStateFlow(0)
    val waitingFilesCount: StateFlow<Int> = _waitingFilesCount.asStateFlow()

    private val _queuedBytes = MutableStateFlow(0L)
    val queuedBytes: StateFlow<Long> = _queuedBytes.asStateFlow()

    private val _transferredBytes = MutableStateFlow(0L)
    val transferredBytes: StateFlow<Long> = _transferredBytes.asStateFlow()

    private var sourceObserver: SourceMediaObserver? = null

    init {
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        scope.launch {
            _pairedDevices.value = db.getAllPairedDevices()
        }
    }

    /**
     * PIXEL ROLE: Starts server and generates a 6-digit PIN & QR payload for companion pairing.
     */
    fun startReceiverMode(port: Int = 8844): PairingPayload {
        syncServer.start(port)
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val payload = syncServer.generatePairingCredentials(ip, port)
        _currentPairing.value = payload
        discoveryManager.startAdvertising(payload.deviceName, port)
        return payload
    }

    fun stopReceiverMode() {
        syncServer.stop()
        discoveryManager.stopAdvertising()
        _currentPairing.value = null
    }

    /**
     * COMPANION ROLE: Pairs with a Pixel receiver via IP/Port and PIN.
     */
    suspend fun pairAsCompanion(host: String, port: Int, pin: String): PairedDevice = withContext(Dispatchers.IO) {
        val deviceName = "Source Phone (${android.os.Build.MODEL})"
        val paired = syncClient.pairWithReceiver(host, port, pin, deviceName)
        refreshPairedDevices()
        startCompanionObserver()
        paired
    }

    /**
     * COMPANION ROLE: Starts incremental MediaStore observer and auto-sync queue.
     */
    fun startCompanionObserver() {
        sourceObserver?.stopObserving()
        sourceObserver = SourceMediaObserver(context, db) { newItems ->
            updateCompanionQueueMetrics()
            scope.launch { runCompanionSyncCycle() }
        }.apply { startObserving() }
        updateCompanionQueueMetrics()
    }

    private fun updateCompanionQueueMetrics() {
        scope.launch {
            val devices = db.getAllPairedDevices()
            if (devices.isNotEmpty()) {
                val items = db.getPendingManifestItems(devices.first().id)
                _waitingFilesCount.value = items.size
                _queuedBytes.value = items.sumOf { it.sizeBytes }
            }
        }
    }

    /**
     * COMPANION ROLE: Runs the continuous automatic cycle with strict backpressure.
     * Respects safe capacity, streams chunks, and pauses when Pixel staging is full.
     */
    suspend fun runCompanionSyncCycle() = withContext(Dispatchers.IO) {
        val devices = db.getAllPairedDevices()
        if (devices.isEmpty()) {
            _companionSyncState.value = "No Paired Pixel"
            return@withContext
        }
        val pixel = devices.first()

        try {
            _companionSyncState.value = "Checking Pixel Capacity..."
            val capacity = syncClient.queryPixelCapacity(pixel)

            if (!capacity.isAcceptingTransfers || capacity.safeAvailableBytes <= 0) {
                _companionSyncState.value = "WAITING FOR PIXEL SPACE"
                return@withContext
            }

            val pendingItems = db.getPendingManifestItems(pixel.id)
            if (pendingItems.isEmpty()) {
                _companionSyncState.value = "Sync Complete"
                return@withContext
            }

            var availableRoom = capacity.safeAvailableBytes

            for (item in pendingItems) {
                // Backpressure check: does this item fit into remaining safe room?
                if (item.sizeBytes > availableRoom) {
                    _companionSyncState.value = "WAITING FOR PIXEL SPACE"
                    break
                }

                _companionSyncState.value = "Transferring: ${item.filename}"

                val inputStream = openSourceStream(item.sourceUri) ?: continue
                val sha256 = item.sha256Hash ?: try {
                    val streamForHash = openSourceStream(item.sourceUri)
                    if (streamForHash != null) StreamingHasher.computeSha256(streamForHash) else ""
                } catch (e: Exception) { "" }

                val success = syncClient.transferFileResumable(
                    device = pixel,
                    manifestItem = item,
                    fileStream = inputStream,
                    expectedSha256 = sha256
                ) { transferred, _ ->
                    _transferredBytes.value += transferred
                }

                if (success) {
                    availableRoom -= item.sizeBytes
                    updateCompanionQueueMetrics()
                } else {
                    _companionSyncState.value = "Transfer Interrupted"
                    break
                }
            }
        } catch (e: Exception) {
            _companionSyncState.value = "Pixel Unavailable (${e.message ?: "Retrying with backoff"})"
        }
    }

    private fun openSourceStream(uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    fun revokeDevice(id: String) {
        scope.launch {
            db.revokeDevice(id)
            refreshPairedDevices()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
