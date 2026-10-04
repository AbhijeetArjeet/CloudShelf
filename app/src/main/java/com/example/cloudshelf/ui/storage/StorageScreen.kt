package com.example.cloudshelf.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.ui.CloudShelfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    val storageStatus by viewModel.storageStatus.collectAsState()
    val activeBatch by viewModel.activeBatch.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Storage", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "ACTUAL STORAGE BREAKDOWN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (storageStatus != null) {
                val status = storageStatus!!
                val freeGb = String.format("%.2f", status.freeBytes / (1024.0 * 1024.0 * 1024.0))
                val totalGb = String.format("%.2f", status.totalBytes / (1024.0 * 1024.0 * 1024.0))
                val stagingMb = String.format("%.1f", status.stagingUsageBytes / (1024.0 * 1024.0))
                val permMb = String.format("%.1f", status.permanentUsageBytes / (1024.0 * 1024.0))
                val reserveGb = String.format("%.1f", status.minReserveBytes / (1024.0 * 1024.0 * 1024.0))
                val targetBatchGb = String.format("%.1f", settings.stagingLimitBytes / (1024.0 * 1024.0 * 1024.0))

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
                            Text("Safe For Next Batch:", fontWeight = FontWeight.Medium)
                            if (status.isSafeForNextBatch) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("YES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NO (Reserve Protected)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        val usedFraction = 1f - (status.freeBytes.toFloat() / status.totalBytes.coerceAtLeast(1L))
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text("$freeGb GB free of $totalGb GB total storage", style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Pixel Free Storage") },
                    trailingContent = { Text("$freeGb GB", fontWeight = FontWeight.SemiBold) }
                )

                ListItem(
                    headlineContent = { Text("CloudShelf Staging Usage") },
                    supportingContent = { Text("Temporary staging folder") },
                    trailingContent = { Text("$stagingMb MB") }
                )

                ListItem(
                    headlineContent = { Text("CloudShelf Permanent Media") },
                    supportingContent = { Text("Retained media copies") },
                    trailingContent = { Text("$permMb MB") }
                )

                ListItem(
                    headlineContent = { Text("Minimum Free-Space Reserve") },
                    supportingContent = { Text("Required headroom for OS and apps") },
                    trailingContent = { Text("$reserveGb GB") }
                )

                ListItem(
                    headlineContent = { Text("Configured Batch Target") },
                    supportingContent = { Text("Max size staged before requiring backup") },
                    trailingContent = { Text("$targetBatchGb GB") }
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
