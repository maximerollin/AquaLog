package com.maximerollin.aqualog.home

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasureGuardTest {
    private val aquariums = listOf("a1", "a2").map { Aquarium(it, it, 100.0, VolumeUnit.LITERS, 0L) }

    @Test
    fun measureIsLiveWhenTheSettledSceneIsTheCurrentAquarium() =
        assertTrue(measureTargetsShownAquarium(aquariums, settledPage = 0, currentId = "a1", scrolling = false))

    @Test
    fun measureWaitsWhileTheSceneIsMoving() =
        assertFalse(measureTargetsShownAquarium(aquariums, settledPage = 0, currentId = "a1", scrolling = true))

    @Test
    fun measureWaitsUntilTheSelectionCatchesUpWithASettledSwipe() {
        assertFalse(measureTargetsShownAquarium(aquariums, settledPage = 1, currentId = "a1", scrolling = false))
        assertTrue(measureTargetsShownAquarium(aquariums, settledPage = 1, currentId = "a2", scrolling = false))
    }

    @Test
    fun aSingleAquariumAlwaysMeasures() =
        assertTrue(measureTargetsShownAquarium(aquariums.take(1), settledPage = 0, currentId = "a1", scrolling = true))

    @Test
    fun pagerFollowsTheCurrentAquarium() {
        assertEquals(1, pagerSyncTarget(currentIndex = 1, pageCount = 2, currentPage = 0))
        assertNull(pagerSyncTarget(currentIndex = 1, pageCount = 2, currentPage = 1))
        assertNull(pagerSyncTarget(currentIndex = 0, pageCount = 1, currentPage = 0))
    }
}
