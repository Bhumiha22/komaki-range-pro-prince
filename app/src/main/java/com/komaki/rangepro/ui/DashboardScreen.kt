package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer
import com.komaki.rangepro.model.ConnState
import com.komaki.rangepro.model.DataSource
import com.komaki.rangepro.range.Confidence
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(c: AppContainer) {
    val sample by c.telemetry.latest.collectAsStateWithLifecycle()
    val status by c.ble.status.collectAsStateWithLifecycle()
    val range by c.range.estimate.collectAsStateWithLifecycle()
    val charge by c.charge.state.collectAsStateWithLifecycle()
    val mac by c.settings.deviceMac.collectAsStateWithLifecycle()
    val now by rememberNow()

    val connect = rememberPermissionGate(Perms.bluetooth, Perms.bluetooth.toList()) { c.ble.connect(mac) }
    val connected = status.state != ConnState.IDLE && status.state != ConnState.GAVE_UP && status.state != ConnState.NO_PERMISSION

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Komaki Range Pro", style = MaterialTheme.typography.headlineSmall)

        if (sample?.source == DataSource.DEMO) Banner("DEMO DATA — simulated values, not from your battery.")
        if (!c.telemetry.decoderVerified) {
            Banner("No verified BMS decoder yet. The app can connect and log raw Bluetooth data, but it shows no battery values until the protocol is decoded from a PackDial capture (see docs/PROTOCOL.md).")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValue("Bluetooth", status.state.name.replace('_', ' ') + if (status.detail.isNotEmpty()) " — ${status.detail}" else "")
                KeyValue("Decoder", c.telemetry.decoderName)
                KeyValue("Last update", sample?.let { "${((now - it.timestampMs) / 1000).coerceAtLeast(0)} s ago" } ?: "no data")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (connected) OutlinedButton(onClick = { c.ble.disconnect() }) { Text("Disconnect") }
                    else Button(onClick = connect) { Text("Connect to BMS") }
                }
            }
        }

        val s = sample
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Battery", fmt(s?.socPercent, 0, "%"), Modifier.weight(1f))
            Tile("Voltage", fmt(s?.packVoltage, 2, "V"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Current", fmt(s?.currentA, 2, "A"), Modifier.weight(1f))
            Tile("Power", fmt(s?.powerW, 0, "W"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Temperature", fmt(s?.maxTempC, 1, "°C"), Modifier.weight(1f))
            val amps = s?.currentA
            Tile(
                "State",
                when {
                    amps == null -> "—"
                    charge.active || amps > 0.5f -> "Charging"
                    amps < -0.5f -> "Discharging"
                    else -> "Idle"
                },
                Modifier.weight(1f),
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Estimated remaining range", style = MaterialTheme.typography.titleMedium)
                val r = range
                if (r == null) {
                    Text("Needs a live battery percentage.")
                } else {
                    val lo = (r.lowKm / 5).roundToInt() * 5
                    val hi = (r.highKm / 5).roundToInt() * 5
                    Text("≈ $lo–$hi km", style = MaterialTheme.typography.headlineMedium)
                    Text("Confidence: ${r.confidence.name.lowercase()}" + if (r.confidence == Confidence.LOW) " (little data)" else "")
                    Text(r.note, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "An estimate from ${"%.2f".format(r.consumptionPctPerKm)} %/km. Speed, load, hills and temperature change it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
