package com.example.cloudshelf.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.model.DeviceRole
import com.example.cloudshelf.ui.CloudShelfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Pixel Receiver, 1 = Source Companion

    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val currentPairing by viewModel.currentPairing.collectAsState()
    val companionSyncState by viewModel.companionSyncState.collectAsState()
    val waitingCount by viewModel.waitingFilesCount.collectAsState()
    val queuedBytes by viewModel.queuedBytes.collectAsState()
    val transferredBytes by viewModel.transferredBytes.collectAsState()
    val discoveredReceivers by viewModel.discoveredReceivers.collectAsState()

    var showPairCompanionDialog by remember { mutableStateOf(false) }
    var targetHost by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("8844") }
    var enteredPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Synchronization", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pixel Receiver") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.startCompanionDiscovery()
                    },
                    text = { Text("Source Companion") }
                )
            }

            if (selectedTab == 0) {
                // ==========================================
                // PIXEL RECEIVER MODE
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("RECEIVER STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Wi-Fi Sync Daemon:", fontWeight = FontWeight.Medium)
                                Badge(
                                    containerColor = if (currentPairing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ) {
                                    Text(if (currentPairing != null) "Listening on Wi-Fi" else "Inactive")
                                }
                            }

                            Text(
                                "This Pixel acts as the staging receiver. It accepts photos over local Wi-Fi, controls backpressure, and waits for Google Photos backup confirmation before clearing local staging copies.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentPairing == null) {
                                    Button(onClick = { viewModel.startReceiverMode() }) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start Receiver & Pair")
                                    }
                                } else {
                                    OutlinedButton(onClick = { viewModel.stopReceiverMode() }) {
                                        Text("Stop Receiver")
                                    }
                                }
                            }
                        }
                    }

                    if (currentPairing != null) {
                        val p = currentPairing!!
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("READY TO PAIR NEW DEVICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Pairing PIN: ${p.pairingPin}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("Receiver Host: ${p.host}:${p.port}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Open CloudShelf on your other device, switch to 'Source Companion', and enter this 6-digit PIN.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("PAIRED SOURCE DEVICES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    if (pairedDevices.isEmpty()) {
                        Text("No companion devices paired yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        pairedDevices.forEach { device ->
                            ListItem(
                                headlineContent = { Text(device.deviceName) },
                                supportingContent = { Text("${device.role.label} • ${device.endpointHost}:${device.endpointPort}") },
                                leadingContent = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.revokeDevice(device.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Revoke device")
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                // ==========================================
                // SOURCE COMPANION MODE
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("COMPANION STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sync Status:", fontWeight = FontWeight.Medium)
                                Badge { Text(companionSyncState) }
                            }

                            val queuedMb = String.format("%.1f", queuedBytes / (1024.0 * 1024.0))
                            val transferredMb = String.format("%.1f", transferredBytes / (1024.0 * 1024.0))

                            Text("Files Waiting: $waitingCount", style = MaterialTheme.typography.bodyMedium)
                            Text("Queued Size: $queuedMb MB", style = MaterialTheme.typography.bodySmall)
                            Text("Transferred: $transferredMb MB", style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "CRITICAL SAFETY INVARIANT: This source phone permanently retains all original photos and videos. Automatic sync is a transfer copy, not a move.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.triggerCompanionSync() }) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                                OutlinedButton(onClick = { showPairCompanionDialog = true }) {
                                    Text("Pair with Pixel")
                                }
                            }
                        }
                    }

                    if (discoveredReceivers.isNotEmpty()) {
                        HorizontalDivider()
                        Text("DISCOVERED PIXEL RECEIVERS (WI-FI)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        discoveredReceivers.forEach { receiver ->
                            ListItem(
                                headlineContent = { Text(receiver.serviceName) },
                                supportingContent = { Text("${receiver.host.hostAddress}:${receiver.port}") },
                                leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        targetHost = receiver.host.hostAddress ?: ""
                                        targetPort = receiver.port.toString()
                                        showPairCompanionDialog = true
                                    }) {
                                        Text("Pair")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPairCompanionDialog) {
        AlertDialog(
            onDismissRequest = { showPairCompanionDialog = false },
            title = { Text("Pair with Pixel Receiver") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("Pixel IP Address") },
                        placeholder = { Text("e.g. 192.168.1.50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { enteredPin = it },
                        label = { Text("6-Digit Pairing PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetHost.isNotBlank() && enteredPin.isNotBlank()) {
                            val port = targetPort.toIntOrNull() ?: 8844
                            viewModel.pairAsCompanion(targetHost, port, enteredPin)
                            showPairCompanionDialog = false
                        }
                    }
                ) {
                    Text("Connect & Pair")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPairCompanionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
