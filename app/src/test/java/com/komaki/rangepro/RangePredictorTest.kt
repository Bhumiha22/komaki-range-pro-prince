package com.komaki.rangepro

import com.komaki.rangepro.range.Confidence
import com.komaki.rangepro.range.RangePredictor
import com.komaki.rangepro.range.RideObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangePredictorTest {
    @Test fun seedOnlyMatchesReportedTest() {
        val e = RangePredictor.estimate(100f, emptyList(), passengerNow = true)!!
        assertEquals(77.2, e.rangeKm, 0.1) // 61 / 0.79
        assertEquals(Confidence.LOW, e.confidence)
        assertTrue(e.usesSeed)
    }

    @Test fun rangeScalesWithSoc() {
        val e = RangePredictor.estimate(50f, emptyList(), passengerNow = true)!!
        assertEquals(38.6, e.rangeKm, 0.1)
    }

    @Test fun bandBracketsEstimate() {
        val e = RangePredictor.estimate(80f, emptyList(), passengerNow = false)!!
        assertTrue(e.lowKm < e.rangeKm && e.rangeKm < e.highKm)
    }

    @Test fun noSeedNoHistoryGivesNull() {
        assertNull(RangePredictor.estimate(80f, emptyList(), true, seed = null))
    }

    @Test fun shortAndTinyRidesAreIgnored() {
        val junk = listOf(RideObservation(1.0, 10.0, null, false), RideObservation(10.0, 2.0, null, false))
        val e = RangePredictor.estimate(100f, junk, passengerNow = false, seed = null)
        assertNull(e)
    }

    @Test fun realHistoryReplacesSeedAndRaisesConfidence() {
        val rides = List(8) { RideObservation(50.0, 50.0, 35.0, false) } // 1 %/km
        val e = RangePredictor.estimate(60f, rides, passengerNow = false)!!
        assertEquals(60.0, e.rangeKm, 0.01)
        assertEquals(Confidence.HIGH, e.confidence)
        assertTrue(!e.usesSeed)
    }

    @Test fun prefersSamePassengerSettingWhenEnoughData() {
        val rides = listOf(
            RideObservation(30.0, 30.0, null, passenger = false), // 1.0 %/km
            RideObservation(30.0, 45.0, null, passenger = true),  // 1.5 %/km
            RideObservation(30.0, 30.0, null, passenger = false),
            RideObservation(30.0, 45.0, null, passenger = true),
        )
        val solo = RangePredictor.estimate(60f, rides, passengerNow = false)!!
        val pax = RangePredictor.estimate(60f, rides, passengerNow = true)!!
        assertNotNull(solo)
        assertTrue(solo.rangeKm > pax.rangeKm)
    }
}
