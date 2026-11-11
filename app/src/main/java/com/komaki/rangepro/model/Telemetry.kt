package com.komaki.rangepro.model

/** Where a sample came from. Only BMS_VERIFIED may feed ride history, range and charging logic. */
enum class DataSource { BMS_VERIFIED, DEMO }

/**
 * One decoded battery reading. Every field is nullable: a decoder only fills what it has verified.
 * Sign convention (the decoder MUST normalise to this): currentA > 0 = charging, < 0 = discharging.
 */
data class BatterySample(
    val timestampMs: Long,
    val source: DataSource,
    val socPercent: Float? = null,
    val packVoltage: Float? = null,
    val currentA: Float? = null,
    val temperaturesC: List<Float> = emptyList(),
    val cellVoltages: List<Float> = emptyList(),
    val chargeMosOn: Boolean? = null,
    val dischargeMosOn: Boolean? = null,
    val protectionFlags: List<String> = emptyList(),
    val cycleCount: Int? = null,
    val capacityUsedAh: Float? = null,
    /** Capacity the BMS reports as configured. NOT a verified battery capacity. */
    val configuredCapacityAh: Float? = null,
) {
    val powerW: Float? get() = if (packVoltage != null && currentA != null) packVoltage * currentA else null
    val maxCellV: Float? get() = cellVoltages.maxOrNull()
    val minCellV: Float? get() = cellVoltages.minOrNull()
    val cellDeltaV: Float? get() = if (cellVoltages.isEmpty()) null else maxCellV!! - minCellV!!
    val maxTempC: Float? get() = temperaturesC.maxOrNull()
}

enum class ConnState { IDLE, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, DISCOVERING, CONNECTED, WAITING_RETRY, GAVE_UP, ERROR }

data class BleStatus(
    val state: ConnState = ConnState.IDLE,
    val detail: String = "",
    val attempt: Int = 0,
)
