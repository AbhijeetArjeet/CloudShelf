package com.example.cloudshelf

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.cloudshelf.ui.CloudShelfViewModel
import com.example.cloudshelf.ui.accounts.AccountsScreen
import com.example.cloudshelf.ui.batches.BatchHistoryScreen
import com.example.cloudshelf.ui.diagnostics.DiagnosticsScreen
import com.example.cloudshelf.ui.failures.FailuresScreen
import com.example.cloudshelf.ui.importing.ImportHubScreen
import com.example.cloudshelf.ui.main.MainScreen
import com.example.cloudshelf.ui.settings.SettingsScreen
import com.example.cloudshelf.ui.storage.StorageScreen
import com.example.cloudshelf.ui.takeout.TakeoutScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val viewModel: CloudShelfViewModel = viewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    viewModel = viewModel,
                    onNavigate = { key -> backStack.add(key) }
                )
            }
            entry<ImportHub> {
                ImportHubScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<TakeoutScreen> {
                TakeoutScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<BatchHistory> {
                BatchHistoryScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<StorageDetail> {
                StorageScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<AccountsScreen> {
                AccountsScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<FailuresScreen> {
                FailuresScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<SettingsScreen> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DiagnosticsScreen> {
                DiagnosticsScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DeviceSync> {
                com.example.cloudshelf.ui.devices.DeviceSyncScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
