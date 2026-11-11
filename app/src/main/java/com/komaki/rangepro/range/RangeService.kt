package com.komaki.rangepro.range

import com.komaki.rangepro.data.TelemetryRepository
import com.komaki.rangepro.db.RideDao
import com.komaki.rangepro.db.RideEntity
import com.komaki.rangepro.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

fun RideEntity.toObservation(): RideObservation? {
    val s = startSoc ?: return null
    val e = endSoc ?: return null
    if (isDemo || s - e <= 0f || distanceM <= 0.0) return null
    val kmh = if (movingMs > 0) distanceM / (movingMs / 1000.0) * 3.6 else null
    return RideObservation(distanceM / 1000.0, (s - e).toDouble(), kmh, passenger)
}

class RangeService(
    scope: CoroutineScope,
    rideDao: RideDao,
    telemetry: TelemetryRepository,
    settings: SettingsStore,
) {
    val estimate: StateFlow<RangeEstimate?> =
        combine(rideDao.observeAll(), telemetry.latest, settings.passenger) { rides, sample, pax ->
            val soc = sample?.socPercent ?: return@combine null
            RangePredictor.estimate(soc, rides.mapNotNull { it.toObservation() }, pax)
        }.stateIn(scope, SharingStarted.Eagerly, null)
}
