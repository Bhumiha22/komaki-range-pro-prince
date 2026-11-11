package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer
import kotlinx.coroutines.launch

@Composable
fun ChargingScreen(c: AppContainer) {
    val scope = rememberCoroutineScope()
    val st by c.charge.state.collectAsStateWithLifecycle()
    val history by c.charge.history.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Charging", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!st.active) {
                        Text("Not charging.")
                        Text(
                            "Charging is detected only from the current the BMS reports (never from an assumed charger rating), so it needs a verified decoder.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        KeyValue("Started", fmtTime(st.startMs))
                        KeyValue("Duration", fmtDuration(st.durationMs))
                        KeyValue("SOC start → now", "${fmt(st.startSoc, 0, "%")} → ${fmt(st.currentSoc, 0, "%")}")
                        KeyValue("Current", fmt(st.currentA, 2, "A"))
                        KeyValue("Power", fmt(st.powerW, 0, "W"))
                        KeyValue("Voltage", fmt(st.voltage, 2, "V"))
                        KeyValue("Energy so far", "%.0f Wh".format(st.energyWh))
                        KeyValue("Est. completion", st.etaMs?.let { fmtTime(it) } ?: "estimating…")
                        Text(
                            "Completion time is extrapolated from the SOC gained so far; charging slows near full, so expect it to run later.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium) }
        if (history.isEmpty()) item { Text("No charging sessions recorded yet.") }
        items(history, key = { it.id }) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(h.startMs), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { scope.launch { c.charge.delete(h.id) } }) { Text("Delete") }
                    }
                    KeyValue("Duration", h.endMs?.let { fmtDuration(it - h.startMs) } ?: "in progress / interrupted")
                    KeyValue("SOC", "${fmt(h.startSoc, 0, "%")} → ${fmt(h.endSoc, 0, "%")}")
                    KeyValue("Peak current", fmt(h.maxCurrentA, 2, "A"))
                    KeyValue("Energy (approx.)", fmt(h.energyWh, 0, "Wh"))
                }
            }
        }
    }
}
