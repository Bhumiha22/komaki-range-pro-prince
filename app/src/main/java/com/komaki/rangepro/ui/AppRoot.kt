package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.komaki.rangepro.AppContainer

enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Battery("Battery", Icons.Filled.Info),
    Rides("Rides", Icons.Filled.Place),
    Charging("Charging", Icons.Filled.Notifications),
    Settings("Settings", Icons.Filled.Settings),
    Diagnostics("Diagnostics", Icons.Filled.Build),
}

@Composable
fun AppRoot(c: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val tabs = listOf(Screen.Home, Screen.Battery, Screen.Rides, Screen.Charging, Screen.Settings)
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s || (s == Screen.Settings && screen == Screen.Diagnostics),
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.Home -> DashboardScreen(c)
                Screen.Battery -> BatteryScreen(c)
                Screen.Rides -> RidesScreen(c)
                Screen.Charging -> ChargingScreen(c)
                Screen.Settings -> SettingsScreen(c) { screen = Screen.Diagnostics }
                Screen.Diagnostics -> DiagnosticsScreen(c) { screen = Screen.Settings }
            }
        }
    }
}
