package com.komaki.rangepro.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.komaki.rangepro.data.TelemetryRepository
import com.komaki.rangepro.db.RideDao
import com.komaki.rangepro.db.RideEntity
import com.komaki.rangepro.db.RidePointEntity
import com.komaki.rangepro.model.DataSource
import com.komaki.rangepro.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RideLive(
    val active: Boolean = false,
    val startMs: Long = 0,
    val durationMs: Long = 0,
    val distanceM: Double = 0.0,
    val speedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val movingMs: Long = 0,
    val startSoc: Float? = null,
    val currentSoc: Float? = null,
    val gpsAccuracyM: Float? = null,
    val hasFix: Boolean = false,
    val demo: Boolean = false,
) {
    val avgMovingKmh: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) * 3.6 else 0.0
    val socUsed: Float? get() = if (startSoc != null && currentSoc != null) startSoc - currentSoc else null
    val pctPerKm: Double? get() = socUsed?.let { if (distanceM >= 500) it / (distanceM / 1000.0) else null }
}

/** GPS ride recording. Position data never leaves the device. */
class RideRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val telemetry: TelemetryRepository,
    private val dao: RideDao,
    private val settings: SettingsStore,
) {
    private val _live = MutableStateFlow(RideLive())
    val live: StateFlow<RideLive> = _live.asStateFlow()

    private val points = mutableListOf<RidePointEntity>()
    private var lastLoc: Location? = null
    private var lastFixMs = 0L

    /** Battery % typed by the user at the start/end of a ride, used when the BMS is not readable. */
    @Volatile var manualStartSoc: Float? = null
    @Volatile var manualEndSoc: Float? = null
    private var ticker: Job? = null
    private var lm: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun socNow(): Pair<Float?, Boolean> {
        val s = telemetry.latest.value
        if (s == null || System.currentTimeMillis() - s.timestampMs > 60_000) return null to false
        return s.socPercent to (s.source == DataSource.DEMO)
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (_live.value.active || !hasLocationPermission()) return false
        points.clear()
        lastLoc = null
        val (liveSoc, demo) = socNow()
        val soc = liveSoc ?: manualStartSoc
        manualStartSoc = null
        val now = System.currentTimeMillis()
        _live.value = RideLive(active = true, startMs = now, startSoc = soc, currentSoc = soc, demo = demo)
        lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val (s, _) = socNow()
                _live.value = _live.value.let { it.copy(durationMs = System.currentTimeMillis() - it.startMs, currentSoc = s ?: it.currentSoc) }
            }
        }
        return true
    }

    private fun onLocation(loc: Location) {
        val cur = _live.value
        if (!cur.active) return
        if (loc.hasAccuracy() && loc.accuracy > 25f) { _live.value = cur.copy(gpsAccuracyM = loc.accuracy); return }
        val prev = lastLoc
        var dist = cur.distanceM
        var moving = cur.movingMs
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        if (prev != null) {
            val d = prev.distanceTo(loc)
            val dtMs = (loc.time - prev.time).coerceAtLeast(0)
            val plausible = dtMs > 0 && d / (dtMs / 1000f) < 60f
            if (d >= 3f && plausible) {
                dist += d
                lastLoc = loc
            }
            if (speed >= 1f && dtMs in 1..10_000) moving += dtMs
        } else {
            lastLoc = loc
        }
        lastFixMs = loc.time
        _live.value = cur.copy(
            distanceM = dist, speedMps = speed, maxSpeedMps = maxOf(cur.maxSpeedMps, speed),
            movingMs = moving, gpsAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null, hasFix = true,
        )
        if (settings.storeRoute.value) {
            points += RidePointEntity(rideId = 0, tMs = loc.time, lat = loc.latitude, lon = loc.longitude, speedMps = speed)
        }
    }

    fun stop() {
        val cur = _live.value
        if (!cur.active) return
        ticker?.cancel()
        runCatching { lm?.removeUpdates(listener) }
        val (soc, _) = socNow()
        val manualEnd = manualEndSoc
        manualEndSoc = null
        val end = System.currentTimeMillis()
        val finalState = cur.copy(active = false, durationMs = end - cur.startMs, currentSoc = soc ?: manualEnd ?: cur.currentSoc)
        _live.value = RideLive()
        val route = points.toList()
        points.clear()
        if (finalState.distanceM < 100.0) return // too short to be a ride
        scope.launch(Dispatchers.IO) {
            val id = dao.insert(
                RideEntity(
                    startMs = finalState.startMs, endMs = end, distanceM = finalState.distanceM,
                    movingMs = finalState.movingMs, maxSpeedMps = finalState.maxSpeedMps,
                    startSoc = finalState.startSoc, endSoc = finalState.currentSoc,
                    passenger = settings.passenger.value, isDemo = finalState.demo,
                ),
            )
            if (route.isNotEmpty()) dao.insertPoints(route.map { it.copy(rideId = id) })
        }
    }

    suspend fun delete(id: Long) {
        dao.deletePoints(id)
        dao.deleteRide(id)
    }
}
