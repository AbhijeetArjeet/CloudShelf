package com.example.cloudshelf.ui.importing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.cloudshelf.AccountsScreen
import com.example.cloudshelf.TakeoutScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHubScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Media", fontWeight = FontWeight.SemiBold) },
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
                "SELECT SOURCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text("Google Photos") },
                supportingContent = { Text("Pick specific albums or photos via official Google Picker session.") },
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(AccountsScreen) }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Google Takeout") },
                supportingContent = { Text("Best for large or complete library transfers without cloud limits.") },
                leadingContent = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigate(TakeoutScreen) }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Local Folder") },
                supportingContent = { Text("Import photos from DCIM, internal folders, or USB drives.") },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { /* Select SAF folder */ }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Laptop / Desktop (Future)") },
                supportingContent = { Text("Direct Wi-Fi transfer from Windows, macOS, or Linux.") },
                leadingContent = { Icon(Icons.Default.Laptop, contentDescription = null) },
                trailingContent = { Text("V2", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
