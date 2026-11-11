package com.komaki.rangepro

import com.komaki.rangepro.charge.ChargeSessionTracker
import com.komaki.rangepro.charge.ChargeSessionTracker.Event
import com.komaki.rangepro.model.BatterySample
import com.komaki.rangepro.model.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeSessionTrackerTest {
    private fun s(tSec: Long, soc: Float, amps: Float) =
        BatterySample(tSec * 1000, DataSource.BMS_VERIFIED, socPercent = soc, packVoltage = 50f, currentA = amps)

    @Test fun startsOnlyWhenCurrentIsPositive() {
        val t = ChargeSessionTracker()
        assertEquals(Event.NONE, t.update(s(0, 50f, -5f)))
        assertEquals(Event.NONE, t.update(s(1, 50f, 0.1f)))
        assertEquals(Event.STARTED, t.update(s(2, 50f, 5f)))
        assertTrue(t.snapshot.active)
        assertEquals(50f, t.snapshot.startSoc)
    }

    @Test fun etaUsesObservedRateNotChargerRating() {
        val t = ChargeSessionTracker()
        t.update(s(0, 20f, 5f))
        assertNull(t.snapshot.etaMs)
        t.update(s(600, 25f, 5f)) // +5% in 10 min -> 0.5 %/min
        val eta = t.snapshot.etaMs
        assertNotNull(eta)
        // 75% left at 0.5 %/min = 150 min
        assertEquals((600_000L + 150L * 60_000L).toDouble(), eta!!.toDouble(), 1000.0)
    }

    @Test fun endsAfterGapAndKeepsSummary() {
        val t = ChargeSessionTracker(endGapMs = 120_000)
        t.update(s(0, 20f, 5f))
        t.update(s(60, 21f, 5f))
        assertEquals(Event.NONE, t.update(s(100, 21f, 0f)))
        assertEquals(Event.ENDED, t.update(s(200, 21f, 0f)))
        assertFalse(t.snapshot.active)
        assertEquals(21f, t.lastEnded!!.currentSoc)
        assertEquals(60_000L, t.lastEnded!!.durationMs)
    }

    @Test fun tickEndsSessionWhenSamplesStop() {
        val t = ChargeSessionTracker(endGapMs = 120_000)
        t.update(s(0, 20f, 5f))
        assertEquals(Event.NONE, t.tick(60_000))
        assertEquals(Event.ENDED, t.tick(130_000))
    }

    @Test fun integratesEnergy() {
        val t = ChargeSessionTracker()
        t.update(s(0, 20f, 5f))
        repeat(10) { t.update(s(1L + it, 20f, 5f)) } // 10 s at 250 W
        assertEquals(250f * 10 / 3600f, t.snapshot.energyWh, 0.01f)
    }
}
