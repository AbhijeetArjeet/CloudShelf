package com.example.cloudshelf

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object ImportHub : NavKey
@Serializable data object TakeoutScreen : NavKey
@Serializable data object BatchHistory : NavKey
@Serializable data object StorageDetail : NavKey
@Serializable data object AccountsScreen : NavKey
@Serializable data object FailuresScreen : NavKey
@Serializable data object SettingsScreen : NavKey
@Serializable data object DiagnosticsScreen : NavKey
@Serializable data object DeviceSync : NavKey
