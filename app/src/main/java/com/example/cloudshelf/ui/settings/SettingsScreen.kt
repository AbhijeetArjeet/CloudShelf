package com.example.cloudshelf.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.model.DuplicatePolicy
import com.example.cloudshelf.model.VerificationMode
import com.example.cloudshelf.ui.CloudShelfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    val batchOptions = listOf(
        500L * 1024 * 1024 to "500 MB",
        1L * 1024 * 1024 * 1024 to "1 GB",
        2L * 1024 * 1024 * 1024 to "2 GB",
        3L * 1024 * 1024 * 1024 to "3 GB (Default)",
        5L * 1024 * 1024 * 1024 to "5 GB",
        10L * 1024 * 1024 * 1024 to "10 GB",
        20L * 1024 * 1024 * 1024 to "20 GB"
    )

    val reserveOptions = listOf(
        3L * 1024 * 1024 * 1024 to "3 GB",
        5L * 1024 * 1024 * 1024 to "5 GB (Default)",
        10L * 1024 * 1024 * 1024 to "10 GB",
        15L * 1024 * 1024 * 1024 to "15 GB"
    )

    var showBatchDialog by remember { mutableStateOf(false) }
    var showReserveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
            Text("STAGING & STORAGE LIMITS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            val currentBatchLabel = batchOptions.find { it.first == settings.stagingLimitBytes }?.second
                ?: "${settings.stagingLimitBytes / (1024 * 1024 * 1024)} GB"
            ListItem(
                headlineContent = { Text("Staging Batch Limit") },
                supportingContent = { Text("Maximum amount staged on Pixel before requiring backup") },
                trailingContent = { Text(currentBatchLabel, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.clickable { showBatchDialog = true }
            )

            val currentReserveLabel = reserveOptions.find { it.first == settings.minFreeReserveBytes }?.second
                ?: "${settings.minFreeReserveBytes / (1024 * 1024 * 1024)} GB"
            ListItem(
                headlineContent = { Text("Minimum Free-Space Reserve") },
                supportingContent = { Text("Staging pauses if free storage falls below this threshold") },
                trailingContent = { Text(currentReserveLabel, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.clickable { showReserveDialog = true }
            )

            HorizontalDivider()
            Text("BACKUP VERIFICATION WORKFLOW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            ListItem(
                headlineContent = { Text("Verification Mode") },
                supportingContent = { Text(settings.verificationMode.displayName) },
                trailingContent = {
                    Switch(
                        checked = settings.verificationMode == VerificationMode.SEMI_AUTOMATIC,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings(
                                settings.copy(verificationMode = if (checked) VerificationMode.SEMI_AUTOMATIC else VerificationMode.MANUAL)
                            )
                        }
                    )
                }
            )

            HorizontalDivider()
            Text("CONSTRAINTS & POLICIES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            ListItem(
                headlineContent = { Text("Transfer on Wi-Fi only") },
                supportingContent = { Text("Prevent unexpected cellular data consumption") },
                trailingContent = {
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(wifiOnly = it)) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Transfer only while charging") },
                supportingContent = { Text("Protect Pixel battery life during large imports") },
                trailingContent = {
                    Switch(
                        checked = settings.chargingOnly,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(chargingOnly = it)) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Deduplication Policy") },
                supportingContent = { Text(settings.duplicatePolicy.displayName) }
            )
        }
    }

    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            title = { Text("Select Staging Batch Limit") },
            text = {
                Column {
                    batchOptions.forEach { (bytes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(stagingLimitBytes = bytes))
                                    showBatchDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = settings.stagingLimitBytes == bytes,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReserveDialog) {
        AlertDialog(
            onDismissRequest = { showReserveDialog = false },
            title = { Text("Select Minimum Free-Space Reserve") },
            text = {
                Column {
                    reserveOptions.forEach { (bytes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(minFreeReserveBytes = bytes))
                                    showReserveDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = settings.minFreeReserveBytes == bytes,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReserveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
