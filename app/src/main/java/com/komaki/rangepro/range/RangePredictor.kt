package com.komaki.rangepro.range

import kotlin.math.sqrt

enum class Confidence { LOW, MEDIUM, HIGH }

data class RideObservation(
    val distanceKm: Double,
    val socUsedPercent: Double,
    val avgSpeedKmh: Double?,
    val passenger: Boolean,
    val isSeed: Boolean = false,
)

data class RangeEstimate(
    val rangeKm: Double,
    val lowKm: Double,
    val highKm: Double,
    val consumptionPctPerKm: Double,
    val confidence: Confidence,
    val ridesUsed: Int,
    val usesSeed: Boolean,
    val note: String,
)

/**
 * Pure SOC-per-km range model built from real ride history.
 *
 * It pools rides by total SOC used / total distance (so long rides count more than short, noisy ones),
 * prefers rides with the same passenger setting, and widens the band when data is thin or inconsistent.
 * It is an estimate: LiFePO4 SOC is non-linear and speed, load, temperature and hills all matter.
 */
object RangePredictor {
    const val MIN_RIDE_KM = 3.0
    const val MIN_SOC_USED = 5.0

    /** The user's own reported test: 100% -> 21% over 61 km, mixed modes, mostly 30-40 km/h, one passenger. */
    val USER_SEED = RideObservation(distanceKm = 61.0, socUsedPercent = 79.0, avgSpeedKmh = null, passenger = true, isSeed = true)

    fun estimate(
        currentSoc: Float,
        history: List<RideObservation>,
        passengerNow: Boolean,
        seed: RideObservation? = USER_SEED,
    ): RangeEstimate? {
        val real = history.filter(::usable)
        // The seed only fills in until there is real history of the app's own.
        val all = if (real.size < 3 && seed != null) real + seed else real
        if (all.isEmpty()) return null

        val sameLoad = all.filter { it.passenger == passengerNow }
        val useSameLoad = sameLoad.sumOf { it.distanceKm } >= 20.0
        val pool = if (useSameLoad) sameLoad else all

        val consumption = pool.sumOf { it.socUsedPercent } / pool.sumOf { it.distanceKm }
        if (consumption <= 0.0) return null

        val spread = if (pool.size < 3) 0.25 else {
            val per = pool.map { it.socUsedPercent / it.distanceKm }
            val mean = per.average()
            val sd = sqrt(per.sumOf { (it - mean) * (it - mean) } / per.size)
            (sd / mean).coerceIn(0.15, 0.5)
        }

        val range = currentSoc / consumption
        val realInPool = pool.count { !it.isSeed }
        val realKm = pool.filter { !it.isSeed }.sumOf { it.distanceKm }
        val confidence = when {
            realInPool >= 8 && realKm >= 300 -> Confidence.HIGH
            realInPool >= 3 && realKm >= 100 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val usesSeed = pool.any { it.isSeed }
        val note = buildString {
            append(if (usesSeed) "Based on your reported 61 km test" else "Based on $realInPool recorded ride(s)")
            if (usesSeed && realInPool > 0) append(" plus $realInPool recorded ride(s)")
            append(if (useSameLoad) ", same passenger setting." else ".")
        }
        return RangeEstimate(range, range * (1 - spread), range * (1 + spread), consumption, confidence, pool.size, usesSeed, note)
    }

    private fun usable(o: RideObservation) = o.distanceKm >= MIN_RIDE_KM && o.socUsedPercent >= MIN_SOC_USED
}
