package com.example.cloudshelf.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.cloudshelf.*
import com.example.cloudshelf.model.BatchState
import com.example.cloudshelf.ui.CloudShelfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: CloudShelfViewModel,
    onNavigate: (NavKey) -> Unit
) {
    val context = LocalContext.current
    val activeBatch by viewModel.activeBatch.collectAsState()
    val storageStatus by viewModel.storageStatus.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val progress by viewModel.currentProgress.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CloudShelf", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { onNavigate(SettingsScreen) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { onNavigate(DiagnosticsScreen) }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Active Staging Cycle
            Text("STAGING CYCLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            
            if (activeBatch != null) {
                val batch = activeBatch!!
                val isConfirmed = batch.state == BatchState.BACKUP_CONFIRMED
                val isWaiting = batch.state == BatchState.READY_FOR_BACKUP || batch.state == BatchState.BACKUP_PENDING

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Batch ${batch.batchIndex} / ~${batch.totalBatchesEstimated}", fontWeight = FontWeight.Bold)
                            Badge(
                                containerColor = if (isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ) {
                                Text(batch.state.label)
                            }
                        }

                        Text("Folder: ${batch.stagingFolder.substringAfterLast("Pictures/")}", style = MaterialTheme.typography.bodySmall)

                        val currentSizeGb = String.format("%.2f", batch.actualSizeBytes / (1024.0 * 1024.0 * 1024.0))
                        val targetSizeGb = String.format("%.2f", batch.targetSizeBytes / (1024.0 * 1024.0 * 1024.0))
                        Text("Staged: $currentSizeGb / $targetSizeGb GB (${batch.itemCount} files)", style = MaterialTheme.typography.bodyMedium)

                        if (progress != null) {
                            val (curr, tot, file) = progress!!
                            LinearProgressIndicator(
                                progress = { curr.toFloat() / tot.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Importing $curr/$tot: $file", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))

                        Text("Cloud Destination: Google Photos Backup", style = MaterialTheme.typography.bodySmall)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("content://media/external/images/media")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open Gallery or Google Photos"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open Photos")
                            }

                            if (!isConfirmed) {
                                Button(
                                    onClick = { viewModel.confirmActiveBatch() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Confirm Backup")
                                }
                            } else {
                                Button(
                                    onClick = { showDeleteConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete Local")
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No active staging batch.", style = MaterialTheme.typography.bodyMedium)
                        Text("Import from Takeout, Google Photos Picker, or local folder to begin staging.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { onNavigate(ImportHub) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Start Import")
                        }
                    }
                }
            }

            // Section 2: Storage Status
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(StorageDetail) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DEVICE STORAGE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val freeGb = storageStatus?.let { String.format("%.1f", it.freeBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "--"
                    Text("$freeGb GB free on Pixel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Storage details")
            }

            // Section 3: Sources
            HorizontalDivider()
            Text("SOURCES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            ListItem(
                headlineContent = { Text("Google Photos") },
                supportingContent = { Text("${accounts.size} connected accounts") },
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(AccountsScreen) }
            )

            ListItem(
                headlineContent = { Text("Google Takeout") },
                supportingContent = { Text("ZIP archive scanner & importer") },
                leadingContent = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(TakeoutScreen) }
            )

            ListItem(
                headlineContent = { Text("Local Folders") },
                supportingContent = { Text("DCIM, Pictures, and external media") },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(ImportHub) }
            )

            ListItem(
                headlineContent = { Text("Device Sync (Wi-Fi)") },
                supportingContent = { Text("Automatic phone-to-Pixel synchronization") },
                leadingContent = { Icon(Icons.Default.Devices, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(DeviceSync) }
            )

            // Section 4: Quick Navigation Hub
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate(BatchHistory) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batch Audit")
                }
                OutlinedButton(
                    onClick = { onNavigate(FailuresScreen) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Failures")
                }
            }
        }
    }

    // Requirement 36: Delete Confirmation Dialog
    if (showDeleteConfirmDialog && activeBatch != null) {
        val batch = activeBatch!!
        val sizeGb = String.format("%.2f", batch.actualSizeBytes / (1024.0 * 1024.0 * 1024.0))
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("DELETE LOCAL STAGING?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Batch: ${batch.batchIndex}")
                    Text("Files: ${batch.itemCount}")
                    Text("Size: $sizeGb GB")
                    Text("Cloud destination: Google Photos")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "CloudShelf will remove these files ONLY from this Pixel staging directory. Your Google Photos cloud copies will remain untouched.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteActiveBatchLocal()
                    }
                ) {
                    Text("Delete Local Copies")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
