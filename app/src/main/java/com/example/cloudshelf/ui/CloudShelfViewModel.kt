package com.example.cloudshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudshelf.data.CloudShelfRepository
import com.example.cloudshelf.model.*
import com.example.cloudshelf.providers.ScanSummary
import com.example.cloudshelf.providers.takeout.GoogleTakeoutProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CloudShelfViewModel(application: Application) : AndroidViewModel(application) {

    val repository = CloudShelfRepository(application)

    val activeBatch: StateFlow<StagingBatch?> = repository.activeBatch
    val storageStatus: StateFlow<StorageStatus?> = repository.storageStatus
    val settings: StateFlow<CloudShelfSettings> = repository.settings
    val allBatches: StateFlow<List<StagingBatch>> = repository.allBatches
    val allFailures: StateFlow<List<FailureRecord>> = repository.allFailures
    val allAccounts: StateFlow<List<Account>> = repository.allAccounts
    val currentProgress: StateFlow<Triple<Int, Int, String>?> = repository.currentProgress

    private val _scanPreview = MutableStateFlow<ScanSummary?>(null)
    val scanPreview: StateFlow<ScanSummary?> = _scanPreview.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun confirmActiveBatch() {
        viewModelScope.launch {
            repository.confirmActiveBatch()
        }
    }

    fun deleteActiveBatchLocal() {
        viewModelScope.launch {
            repository.deleteActiveBatchLocal()
        }
    }

    fun updateSettings(newSettings: CloudShelfSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun addAccount(email: String, name: String) {
        viewModelScope.launch {
            repository.addAccount(email, name)
        }
    }

    fun removeAccount(id: String) {
        viewModelScope.launch {
            repository.removeAccount(id)
        }
    }

    fun clearFailures() {
        viewModelScope.launch {
            repository.clearAllFailures()
        }
    }

    fun scanTakeoutArchives(archiveFiles: List<File>) {
        viewModelScope.launch {
            _isScanning.value = true
            val provider = GoogleTakeoutProvider(archiveFiles, settings.value.stagingLimitBytes)
            val summary = provider.scanSource()
            _scanPreview.value = summary
            _isScanning.value = false
        }
    }

    fun startTakeoutImport(summary: ScanSummary, archiveFiles: List<File>) {
        viewModelScope.launch {
            val provider = GoogleTakeoutProvider(archiveFiles, settings.value.stagingLimitBytes)
            repository.runImportPipeline(summary, provider)
        }
    }

    // ==========================================
    // Device Sync Subsystem
    // ==========================================

    val pairedDevices: StateFlow<List<PairedDevice>> = repository.deviceSyncManager.pairedDevices
    val currentPairing: StateFlow<PairingPayload?> = repository.deviceSyncManager.currentPairing
    val companionSyncState: StateFlow<String> = repository.deviceSyncManager.companionSyncState
    val waitingFilesCount: StateFlow<Int> = repository.deviceSyncManager.waitingFilesCount
    val queuedBytes: StateFlow<Long> = repository.deviceSyncManager.queuedBytes
    val transferredBytes: StateFlow<Long> = repository.deviceSyncManager.transferredBytes
    val discoveredReceivers = repository.deviceSyncManager.discoveryManager.discoveredReceivers

    fun startReceiverMode() {
        repository.deviceSyncManager.startReceiverMode()
    }

    fun stopReceiverMode() {
        repository.deviceSyncManager.stopReceiverMode()
    }

    fun startCompanionDiscovery() {
        repository.deviceSyncManager.discoveryManager.startDiscovery()
    }

    fun stopCompanionDiscovery() {
        repository.deviceSyncManager.discoveryManager.stopDiscovery()
    }

    fun pairAsCompanion(host: String, port: Int, pin: String) {
        viewModelScope.launch {
            repository.deviceSyncManager.pairAsCompanion(host, port, pin)
        }
    }

    fun triggerCompanionSync() {
        viewModelScope.launch {
            repository.deviceSyncManager.runCompanionSyncCycle()
        }
    }

    fun revokeDevice(id: String) {
        repository.deviceSyncManager.revokeDevice(id)
    }
}
