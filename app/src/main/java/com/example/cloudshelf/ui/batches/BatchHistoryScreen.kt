package com.example.cloudshelf.ui.batches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.ui.CloudShelfViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchHistoryScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    val batches by viewModel.allBatches.collectAsState()
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch Audit Trail", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (batches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text("No batch history recorded yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(batches) { batch ->
                    val sizeGb = String.format("%.2f", batch.actualSizeBytes / (1024.0 * 1024.0 * 1024.0))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Batch ${String.format("%03d", batch.batchIndex)}", fontWeight = FontWeight.Bold)
                                Badge { Text(batch.state.label) }
                            }

                            Text("$sizeGb GB (${batch.itemCount} files)", style = MaterialTheme.typography.bodyMedium)

                            if (batch.importedAt != null) {
                                Text("Imported: ${dateFormat.format(Date(batch.importedAt))}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (batch.backupConfirmedAt != null) {
                                Text("Backup Confirmed: ${dateFormat.format(Date(batch.backupConfirmedAt))} (${batch.confirmationMethod.name})", style = MaterialTheme.typography.bodySmall)
                            }
                            if (batch.localDeletedAt != null) {
                                Text("Deleted Locally: ${dateFormat.format(Date(batch.localDeletedAt))}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (!batch.errorMessage.isNullOrBlank()) {
                                Text("Error: ${batch.errorMessage}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
