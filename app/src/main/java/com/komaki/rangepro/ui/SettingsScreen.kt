package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(c: AppContainer, openDiagnostics: () -> Unit) {
    val st = c.settings
    val mac by st.deviceMac.collectAsStateWithLifecycle()
    val demo by st.demoMode.collectAsStateWithLifecycle()
    val pax by st.passenger.collectAsStateWithLifecycle()
    val route by st.storeRoute.collectAsStateWithLifecycle()
    val verified by st.verifiedCapacityAh.collectAsStateWithLifecycle()

    var macText by remember(mac) { mutableStateOf(mac) }
    var capText by remember(verified) { mutableStateOf(verified?.toString() ?: "") }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = macText,
            onValueChange = { macText = it.uppercase(); if (android.bluetooth.BluetoothAdapter.checkBluetoothAddress(macText.trim())) st.setMac(macText) },
            label = { Text("BMS Bluetooth MAC") },
            supportingText = { Text("Close PackDial first: the BMS may allow only one connection.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = capText,
            onValueChange = { capText = it; st.setVerifiedCapacity(it.toFloatOrNull()) },
            label = { Text("Verified battery capacity (Ah), optional") },
            supportingText = { Text("Leave blank unless you have measured or confirmed it. The BMS's configured value is not treated as verified.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SwitchRow("Passenger on board", "Used to pick matching rides for range prediction.", pax) { st.setPassenger(it) }
        SwitchRow("Save route points", "Stores GPS points on this device only. Off = distance and speed only.", route) { st.setStoreRoute(it) }
        SwitchRow("Demo mode", "Shows clearly labelled SIMULATED data so you can look at the UI. Disables real decoding while on.", demo) { st.setDemo(it) }

        OutlinedButton(onClick = openDiagnostics) { Text("Open diagnostics (raw Bluetooth log)") }

        Text(
            "Privacy: all battery, ride and location data stays on this phone. The app has no network permission.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
