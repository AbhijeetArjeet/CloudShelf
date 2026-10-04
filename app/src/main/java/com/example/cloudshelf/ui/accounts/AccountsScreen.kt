package com.example.cloudshelf.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cloudshelf.model.Account
import com.example.cloudshelf.ui.CloudShelfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: CloudShelfViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.allAccounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Accounts", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SOURCE ACCOUNTS VS BACKUP ACCOUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Source accounts are where your existing photos originate. The backup account is the Google Account configured in Google Photos on this Pixel.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("CONNECTED SOURCE ACCOUNTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            if (accounts.isEmpty()) {
                Text("No source Google accounts added yet. Add an account to initiate official Google Photos Picker sessions.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { showAddDialog = true }) {
                    Text("Add Google Account")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts) { account ->
                        ListItem(
                            headlineContent = { Text(account.displayName) },
                            supportingContent = { Text("${account.email} • ${account.authState.name}") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeAccount(account.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Account")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Connect Source Google Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Google Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Account Label / Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newEmail.isNotBlank()) {
                            viewModel.addAccount(newEmail, newName.ifBlank { newEmail })
                            newEmail = ""
                            newName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
