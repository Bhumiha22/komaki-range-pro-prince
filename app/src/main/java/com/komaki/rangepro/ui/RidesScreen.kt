package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer
import com.komaki.rangepro.gps.RideTrackingService
import kotlinx.coroutines.launch

@Composable
fun RidesScreen(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by c.recorder.live.collectAsStateWithLifecycle()
    val rides by c.rideDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val passenger by c.settings.passenger.collectAsStateWithLifecycle()
    val latest by c.telemetry.latest.collectAsStateWithLifecycle()
    var startSoc by remember { mutableStateOf("") }
    var endSoc by remember { mutableStateOf("") }
    var showStop by remember { mutableStateOf(false) }

    val start = rememberPermissionGate(Perms.ride, listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
        RideTrackingService.start(ctx)
    }

    if (showStop) {
        AlertDialog(
            onDismissRequest = { showStop = false },
            title = { Text("Finish ride") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type the battery % shown now. Leave it blank to use the BMS value if one is available.")
                    OutlinedTextField(
                        value = endSoc,
                        onValueChange = { endSoc = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                        label = { Text("Battery %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    c.recorder.manualEndSoc = endSoc.toFloatOrNull()?.coerceIn(0f, 100f)
                    showStop = false
                    RideTrackingService.stop(ctx)
                }) { Text("Stop ride") }
            },
            dismissButton = { TextButton(onClick = { showStop = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Rides", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!live.active) {
                        Text("Passenger on this ride: ${if (passenger) "yes" else "no"} (change in Settings)")
                        Text("Starting SOC is taken from the BMS if it is connected and fresh; otherwise the ride is saved without SOC and not used for range.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = startSoc,
                            onValueChange = { startSoc = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                            label = { Text("Start battery % (from your display)") },
                            placeholder = { Text(latest?.takeIf { System.currentTimeMillis() - it.timestampMs < 600_000 }?.socPercent?.let { "%.0f".format(it) } ?: "") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(onClick = {
                            c.recorder.manualStartSoc = (startSoc.toFloatOrNull() ?: latest?.takeIf { System.currentTimeMillis() - it.timestampMs < 600_000 }?.socPercent)?.coerceIn(0f, 100f)
                            start()
                        }) { Text("Start ride") }
                    } else {
                        if (live.demo) Banner("Demo mode: this ride will be flagged DEMO and excluded from range history.")
                        KeyValue("GPS", if (live.hasFix) "fix, ±${fmt(live.gpsAccuracyM, 0, "m")}" else "waiting for fix…")
                        KeyValue("Distance", "%.2f km".format(live.distanceM / 1000.0))
                        KeyValue("Speed", "%.0f km/h".format(live.speedMps * 3.6))
                        KeyValue("Avg (moving)", "%.0f km/h".format(live.avgMovingKmh))
                        KeyValue("Max", "%.0f km/h".format(live.maxSpeedMps * 3.6))
                        KeyValue("Duration", fmtDuration(live.durationMs))
                        KeyValue("SOC start → now", "${fmt(live.startSoc, 0, "%")} → ${fmt(live.currentSoc, 0, "%")}")
                        KeyValue("Consumption", live.pctPerKm?.let { "%.2f %%/km".format(it) } ?: "—")
                        Button(onClick = { endSoc = ""; showStop = true }) { Text("Stop ride") }
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium) }
        if (rides.isEmpty()) item { Text("No rides recorded yet.") }
        items(rides, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(r.startMs) + if (r.isDemo) "  [DEMO]" else "", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { scope.launch { c.recorder.delete(r.id) } }) { Text("Delete") }
                    }
                    val km = r.distanceM / 1000.0
                    KeyValue("Distance / time", "%.2f km / %s".format(km, fmtDuration(r.endMs - r.startMs)))
                    KeyValue("Avg / max speed", "%.0f / %.0f km/h".format(if (r.movingMs > 0) km / (r.movingMs / 3_600_000.0) else 0.0, r.maxSpeedMps * 3.6))
                    KeyValue("SOC", "${fmt(r.startSoc, 0, "%")} → ${fmt(r.endSoc, 0, "%")}")
                    val used = if (r.startSoc != null && r.endSoc != null) r.startSoc - r.endSoc else null
                    KeyValue("Consumption", if (used != null && km >= 0.5) "%.2f %%/km".format(used / km) else "—")
                    KeyValue("Passenger", if (r.passenger) "yes" else "no")
                }
            }
        }
    }
}
