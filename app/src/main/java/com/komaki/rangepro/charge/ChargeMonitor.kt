package com.komaki.rangepro.charge

import com.komaki.rangepro.data.TelemetryRepository
import com.komaki.rangepro.db.ChargeDao
import com.komaki.rangepro.db.ChargeSessionEntity
import com.komaki.rangepro.model.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Feeds real BMS samples to the tracker and persists sessions. Demo samples are ignored. */
class ChargeMonitor(
    scope: CoroutineScope,
    telemetry: TelemetryRepository,
    private val dao: ChargeDao,
) {
    private val tracker = ChargeSessionTracker()
    private val mutex = Mutex()
    private var rowId = 0L

    private val _state = MutableStateFlow(ChargeSnapshot())
    val state: StateFlow<ChargeSnapshot> = _state.asStateFlow()
    val history = dao.observeAll()

    init {
        scope.launch {
            telemetry.samples.filter { it.source == DataSource.BMS_VERIFIED }.collect { s -> step { tracker.update(s) } }
        }
        scope.launch {
            while (true) {
                delay(30_000)
                step { tracker.tick(System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun step(block: () -> ChargeSessionTracker.Event) = mutex.withLock {
        when (block()) {
            ChargeSessionTracker.Event.STARTED -> {
                val s = tracker.snapshot
                rowId = dao.insert(ChargeSessionEntity(startMs = s.startMs, startSoc = s.startSoc))
            }
            ChargeSessionTracker.Event.ENDED -> tracker.lastEnded?.let { e ->
                dao.update(
                    ChargeSessionEntity(
                        id = rowId, startMs = e.startMs, endMs = e.endMs, startSoc = e.startSoc,
                        endSoc = e.currentSoc, maxCurrentA = e.maxCurrentA, energyWh = e.energyWh,
                    ),
                )
            }
            ChargeSessionTracker.Event.NONE -> Unit
        }
        _state.value = tracker.snapshot
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
