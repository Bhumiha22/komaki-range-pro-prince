package com.komaki.rangepro.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komaki.rangepro.AppContainer
import com.komaki.rangepro.bms.BleUuids

@Composable
fun DiagnosticsScreen(c: AppContainer, back: () -> Unit) {
    val ctx = LocalContext.current
    val status by c.ble.status.collectAsStateWithLifecycle()
    val services by c.ble.services.collectAsStateWithLifecycle()
    val lines by c.ble.log.lines.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
        Banner(
            "Listening only. The app enables notifications on FFF1 and logs what arrives. With the current decoder it never writes anything to the BMS. " +
                "Share this log to help decode the protocol.",
            warn = false,
        )
        KeyValue("Connection", "${status.state} ${status.detail}")
        KeyValue("Decoder", c.telemetry.decoderName)
        KeyValue("Verified", if (c.telemetry.decoderVerified) "yes" else "no")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { c.ble.readCharacteristic(BleUuids.FFF1) }) { Text("Read FFF1") }
            OutlinedButton(onClick = { c.ble.log.clear() }) { Text("Clear log") }
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, c.ble.log.asText())
                ctx.startActivity(Intent.createChooser(send, "Share BLE log"))
            }) { Text("Share log") }
        }

        Text("GATT services", style = MaterialTheme.typography.titleMedium)
        Text(if (services.isEmpty()) "(not connected yet)" else services.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)

        Text("Event log (latest ${lines.size})", style = MaterialTheme.typography.titleMedium)
        Text(
            if (lines.isEmpty()) "(empty)" else lines.takeLast(120).joinToString("\n") { it.text },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        OutlinedButton(onClick = back) { Text("Back to settings") }
    }
}
