package com.maximerollin.aqualog.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorsTest {
    @Test
    fun `a tank's volume comes from centimetres or from inches`() {
        assertEquals(120.0, tankVolumeLiters(80.0, 30.0, 50.0, VolumeUnit.LITERS)!!, 1e-9)
        // A standard 20-gallon « high »: 24 × 12 × 16 in.
        assertEquals(19.95, litersToUsGallons(tankVolumeLiters(24.0, 12.0, 16.0, VolumeUnit.US_GALLONS)!!), 0.01)
        assertNull(tankVolumeLiters(80.0, 0.0, 50.0, VolumeUnit.LITERS))
        assertNull(tankVolumeLiters(80.0, -1.0, 50.0, VolumeUnit.LITERS))
    }

    @Test
    fun `a water change is a share of the volume, between nothing and all of it`() {
        assertEquals(30.0, waterChangeVolume(120.0, 25.0)!!, 1e-9)
        assertEquals(0.0, waterChangeVolume(120.0, 0.0)!!, 1e-9)
        assertNull(waterChangeVolume(120.0, 101.0))
        assertNull(waterChangeVolume(0.0, 25.0))
    }

    @Test
    fun `a water change dilutes towards the new water's concentration`() {
        assertEquals(20.0, concentrationAfterWaterChange(40.0, 0.0, 50.0)!!, 1e-9)
        assertEquals(32.5, concentrationAfterWaterChange(40.0, 10.0, 25.0)!!, 1e-9)
        assertEquals(10.0, concentrationAfterWaterChange(40.0, 10.0, 100.0)!!, 1e-9)
        assertNull(concentrationAfterWaterChange(-1.0, 0.0, 50.0))
    }

    @Test
    fun `a label's dose scales with the tank`() {
        assertEquals(12.0, doseForVolume(5.0, 50.0, 120.0)!!, 1e-9)
        assertNull(doseForVolume(5.0, 0.0, 120.0))
    }
}
