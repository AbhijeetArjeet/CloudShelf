package com.example.cloudshelf.ui.takeout

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.ui.CloudShelfViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoutScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scanPreview by viewModel.scanPreview.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val storageStatus by viewModel.storageStatus.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isDryRun by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val localFiles = uris.mapNotNull { uri ->
                try {
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "archive.zip"
                    val tempArchive = File(context.cacheDir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempArchive).use { out ->
                            input.copyTo(out)
                        }
                    }
                    tempArchive
                } catch (e: Exception) {
                    null
                }
            }
            selectedFiles = localFiles
            if (localFiles.isNotEmpty()) {
                viewModel.scanTakeoutArchives(localFiles)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Takeout Import", fontWeight = FontWeight.SemiBold) },
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
                "ARCHIVE SCANNER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Google Takeout is the recommended method for transferring complete photo libraries safely without cloud API restrictions.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { filePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedFiles.isEmpty()) "Select Takeout Archive(s)" else "Change Archives (${selectedFiles.size} selected)")
                    }
                }
            }

            if (isScanning) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Inspecting archive headers and sidecars...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (scanPreview != null) {
                val summary = scanPreview!!
                val sizeGb = String.format("%.2f", summary.totalSizeBytes / (1024.0 * 1024.0 * 1024.0))
                val stagingLimitGb = String.format("%.1f", settings.stagingLimitBytes / (1024.0 * 1024.0 * 1024.0))
                val freeStorageGb = storageStatus?.let { String.format("%.1f", it.freeBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "--"

                HorizontalDivider()
                Text("SCAN PREVIEW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                ListItem(headlineContent = { Text("Source Archive") }, trailingContent = { Text(summary.sourceName) })
                ListItem(headlineContent = { Text("Archive Media Size") }, trailingContent = { Text("$sizeGb GB") })
                ListItem(headlineContent = { Text("Photos Discovered") }, trailingContent = { Text("${summary.photoCount}") })
                ListItem(headlineContent = { Text("Videos Discovered") }, trailingContent = { Text("${summary.videoCount}") })
                ListItem(headlineContent = { Text("Estimated Batches") }, trailingContent = { Text("~${summary.estimatedBatches} (at $stagingLimitGb GB limit)") })
                ListItem(headlineContent = { Text("Available Pixel Storage") }, trailingContent = { Text("$freeStorageGb GB") })

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { isDryRun = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dry Run (Preview)")
                    }

                    Button(
                        onClick = {
                            viewModel.startTakeoutImport(summary, selectedFiles)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start Import")
                    }
                }

                if (isDryRun) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            "Dry run complete: ${summary.totalItems} media entries verified, ${summary.estimatedBatches} staging batches planned. No files written to disk.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
