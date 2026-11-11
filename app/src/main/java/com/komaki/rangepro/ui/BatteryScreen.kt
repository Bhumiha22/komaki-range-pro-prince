package com.komaki.rangepro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer
import com.komaki.rangepro.model.DataSource

@Composable
fun BatteryScreen(c: AppContainer) {
    val s by c.telemetry.latest.collectAsStateWithLifecycle()
    val verifiedAh by c.settings.verifiedCapacityAh.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Battery monitor", style = MaterialTheme.typography.headlineSmall)
        val cur = s
        if (cur?.source == DataSource.DEMO) Banner("DEMO DATA — simulated values.")
        if (cur == null) {
            Banner("No battery data. Connect on the Home tab. Values appear only from a verified BMS decoder.", warn = false)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Highest cell", fmt(cur?.maxCellV, 3, "V"), Modifier.weight(1f))
            Tile("Lowest cell", fmt(cur?.minCellV, 3, "V"), Modifier.weight(1f))
            Tile("Difference", fmt(cur?.cellDeltaV?.times(1000f), 0, "mV"), Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cell voltages", style = MaterialTheme.typography.titleMedium)
                if (cur == null || cur.cellVoltages.isEmpty()) Text("—")
                else cur.cellVoltages.forEachIndexed { i, v ->
                    val tag = when {
                        v == cur.maxCellV && cur.cellVoltages.size > 1 -> "  ▲ highest"
                        v == cur.minCellV && cur.cellVoltages.size > 1 -> "  ▼ lowest"
                        else -> ""
                    }
                    KeyValue("Cell ${i + 1}", "%.3f V$tag".format(v))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Temperatures", style = MaterialTheme.typography.titleMedium)
                if (cur == null || cur.temperaturesC.isEmpty()) Text("—")
                else cur.temperaturesC.forEachIndexed { i, t -> KeyValue("Sensor ${i + 1}", fmt(t, 1, "°C")) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                KeyValue("Charge MOS", cur?.chargeMosOn?.let { if (it) "ON" else "OFF" } ?: "not reported")
                KeyValue("Discharge MOS", cur?.dischargeMosOn?.let { if (it) "ON" else "OFF" } ?: "not reported")
                KeyValue("Protection", if (cur == null) "not reported" else cur.protectionFlags.ifEmpty { listOf("none reported") }.joinToString())
                KeyValue("Cycle count", cur?.cycleCount?.toString() ?: "not reported")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Capacity", style = MaterialTheme.typography.titleMedium)
                KeyValue("Configured in BMS", fmt(cur?.configuredCapacityAh, 1, "Ah") + if (cur?.configuredCapacityAh != null) " (unverified)" else "")
                KeyValue("Used (per BMS)", fmt(cur?.capacityUsedAh, 1, "Ah"))
                KeyValue("Verified capacity", verifiedAh?.let { "%.1f Ah (entered by you)".format(it) } ?: "not set")
                Text(
                    "The configured value is whatever the BMS is set to, not a measured battery capacity.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
