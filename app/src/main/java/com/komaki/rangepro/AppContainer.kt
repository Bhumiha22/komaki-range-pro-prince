package com.komaki.rangepro

import android.app.Application
import com.komaki.rangepro.ble.BleManager
import com.komaki.rangepro.bms.BmsDecoders
import com.komaki.rangepro.charge.ChargeMonitor
import com.komaki.rangepro.data.TelemetryRepository
import com.komaki.rangepro.db.AppDatabase
import com.komaki.rangepro.gps.RideRecorder
import com.komaki.rangepro.range.RangeService
import com.komaki.rangepro.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled service locator; one instance for the process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val db = AppDatabase.build(app)
    val decoder = BmsDecoders.active
    val ble = BleManager(app, scope, decoder)
    val telemetry = TelemetryRepository(scope, ble, decoder, settings)
    val charge = ChargeMonitor(scope, telemetry, db.chargeDao())
    val recorder = RideRecorder(app, scope, telemetry, db.rideDao(), settings)
    val range = RangeService(scope, db.rideDao(), telemetry, settings)
    val rideDao get() = db.rideDao()
}

class KomakiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
