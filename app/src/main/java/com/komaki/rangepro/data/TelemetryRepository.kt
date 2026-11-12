package com.komaki.rangepro.data

import com.komaki.rangepro.ble.BleManager
import com.komaki.rangepro.bms.BmsDecoder
import com.komaki.rangepro.model.BatterySample
import com.komaki.rangepro.model.DataSource
import com.komaki.rangepro.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Turns raw BLE frames into [BatterySample]s via the active decoder. Demo data is clearly tagged. */
class TelemetryRepository(
    scope: CoroutineScope,
    ble: BleManager,
    private val decoder: BmsDecoder,
    private val settings: SettingsStore,
) {
    private val _latest = MutableStateFlow<BatterySample?>(null)
    val latest: StateFlow<BatterySample?> = _latest.asStateFlow()

    private val _samples = MutableSharedFlow<BatterySample>(extraBufferCapacity = 64)
    val samples: SharedFlow<BatterySample> = _samples.asSharedFlow()

    val decoderName: String get() = decoder.name
    val decoderVerified: Boolean get() = decoder.isVerified

    /** Battery % typed in by the user (read off the scooter display). Never mixed up with BMS data. */
    fun setManualSoc(percent: Float) {
        if (settings.demoMode.value) return
        publish(BatterySample(System.currentTimeMillis(), DataSource.MANUAL, socPercent = percent.coerceIn(0f, 100f)))
    }

    /** Latest sample only if it is real, and fresh. */
    fun freshRealSample(maxAgeMs: Long = 60_000): BatterySample? =
        _latest.value?.takeIf { it.source == DataSource.BMS_VERIFIED && System.currentTimeMillis() - it.timestampMs <= maxAgeMs }

    private fun publish(s: BatterySample) {
        _latest.value = s
        _samples.tryEmit(s)
    }

    init {
        scope.launch {
            ble.frames.collect { frame ->
                if (!settings.demoMode.value) {
                    decoder.feed(frame, System.currentTimeMillis()).forEach { publish(it) }
                }
            }
        }
        scope.launch {
            settings.demoMode.collectLatest { on ->
                _latest.value = null
                if (on) {
                    var t = 0
                    while (true) {
                        publish(demoSample(t++))
                        delay(1000)
                    }
                }
            }
        }
    }

    /** Synthetic data for trying the UI. Always tagged DataSource.DEMO and never stored as history. */
    private fun demoSample(t: Int): BatterySample {
        val cells = List(15) { i -> 3.30f + 0.01f * sin((t + i * 3) / 10.0).toFloat() }
        val current = -8f + 3f * sin(t / 8.0).toFloat()
        return BatterySample(
            timestampMs = System.currentTimeMillis(),
            source = DataSource.DEMO,
            socPercent = (80f - (t % 600) / 10f).coerceIn(5f, 100f),
            packVoltage = cells.sum(),
            currentA = current,
            temperaturesC = listOf(31f, 30f, 29f),
            cellVoltages = cells,
            chargeMosOn = true,
            dischargeMosOn = true,
            protectionFlags = emptyList(),
        )
    }
}
