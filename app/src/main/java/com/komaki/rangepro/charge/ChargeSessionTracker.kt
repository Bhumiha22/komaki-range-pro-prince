package com.komaki.rangepro.charge

import com.komaki.rangepro.model.BatterySample

data class ChargeSnapshot(
    val active: Boolean = false,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val startSoc: Float? = null,
    val currentSoc: Float? = null,
    val currentA: Float? = null,
    val powerW: Float? = null,
    val voltage: Float? = null,
    val durationMs: Long = 0,
    /** Absolute estimated completion time, from the SOC gain observed so far. Null until enough data. */
    val etaMs: Long? = null,
    val energyWh: Float = 0f,
    val maxCurrentA: Float = 0f,
)

/**
 * Pure state machine. Charging is decided from the BMS-reported current only (never an assumed charger rating).
 * Requires the decoder to normalise current so that positive = charging.
 */
class ChargeSessionTracker(
    private val startThresholdA: Float = 0.5f,
    private val endGapMs: Long = 120_000,
    private val minEtaWindowMs: Long = 300_000,
) {
    enum class Event { NONE, STARTED, ENDED }

    var snapshot = ChargeSnapshot()
        private set
    var lastEnded: ChargeSnapshot? = null
        private set

    private var lastChargingMs = 0L
    private var lastSampleMs = -1L
    private var energyWh = 0.0
    private var maxA = 0f

    fun update(s: BatterySample): Event {
        val a = s.currentA ?: return tick(s.timestampMs)
        val charging = a >= startThresholdA
        val dt = if (lastSampleMs >= 0) (s.timestampMs - lastSampleMs).coerceIn(0, 10_000) else 0
        lastSampleMs = s.timestampMs

        if (!snapshot.active) {
            if (!charging) return Event.NONE
            energyWh = 0.0
            maxA = a
            lastChargingMs = s.timestampMs
            snapshot = build(s, startMs = s.timestampMs, startSoc = s.socPercent)
            return Event.STARTED
        }
        if (charging) {
            lastChargingMs = s.timestampMs
            energyWh += (s.powerW ?: 0f) * dt / 3_600_000.0
            if (a > maxA) maxA = a
        }
        snapshot = build(s, snapshot.startMs, snapshot.startSoc)
        return tick(s.timestampMs)
    }

    /** Call periodically so a session still ends if samples stop arriving (BLE dropped). */
    fun tick(nowMs: Long): Event {
        if (snapshot.active && nowMs - lastChargingMs >= endGapMs) {
            lastEnded = snapshot.copy(active = false, endMs = lastChargingMs, durationMs = lastChargingMs - snapshot.startMs, etaMs = null)
            snapshot = ChargeSnapshot()
            return Event.ENDED
        }
        return Event.NONE
    }

    private fun build(s: BatterySample, startMs: Long, startSoc: Float?): ChargeSnapshot {
        val elapsed = s.timestampMs - startMs
        val gain = if (s.socPercent != null && startSoc != null) s.socPercent - startSoc else null
        val eta = if (gain != null && gain >= 1f && elapsed >= minEtaWindowMs && s.socPercent != null) {
            val ratePerMs = gain / elapsed.toDouble()
            s.timestampMs + ((100f - s.socPercent) / ratePerMs).toLong()
        } else null
        return ChargeSnapshot(
            active = true, startMs = startMs, startSoc = startSoc,
            currentSoc = s.socPercent, currentA = s.currentA, powerW = s.powerW, voltage = s.packVoltage,
            durationMs = elapsed, etaMs = eta, energyWh = energyWh.toFloat(), maxCurrentA = maxA,
        )
    }
}
