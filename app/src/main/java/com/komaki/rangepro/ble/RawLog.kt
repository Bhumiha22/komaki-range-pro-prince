package com.komaki.rangepro.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(val timestampMs: Long, val text: String)

/** In-memory ring buffer of BLE events for the Diagnostics screen. */
class RawLog(private val capacity: Int = 400) {
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    fun add(text: String) {
        val line = LogLine(System.currentTimeMillis(), text)
        _lines.update { (it + line).takeLast(capacity) }
    }

    fun clear() { _lines.value = emptyList() }

    fun asText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return _lines.value.joinToString("\n") { "${fmt.format(Date(it.timestampMs))}  ${it.text}" }
    }

    companion object {
        fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
    }
}
