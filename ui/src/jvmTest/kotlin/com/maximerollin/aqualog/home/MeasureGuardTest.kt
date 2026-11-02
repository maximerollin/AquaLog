package com.maximerollin.aqualog.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasureGuardTest {
    @Test
    fun measureIsLiveWhenNothingIsPending() =
        assertTrue(measureTargetsShownAquarium(aquariumCount = 2, scrolling = false, swipeSelectionPending = false))

    @Test
    fun measureWaitsWhileTheSceneIsMoving() =
        assertFalse(measureTargetsShownAquarium(aquariumCount = 2, scrolling = true, swipeSelectionPending = false))

    @Test
    fun measureWaitsUntilTheSelectionCatchesUpWithASwipe() =
        assertFalse(measureTargetsShownAquarium(aquariumCount = 2, scrolling = false, swipeSelectionPending = true))

    @Test
    fun aSingleAquariumAlwaysMeasures() =
        assertTrue(measureTargetsShownAquarium(aquariumCount = 1, scrolling = true, swipeSelectionPending = true))

    @Test
    fun pagerFollowsTheCurrentAquarium() {
        assertEquals(1, pagerSyncTarget(currentIndex = 1, pageCount = 2, currentPage = 0))
        assertNull(pagerSyncTarget(currentIndex = 1, pageCount = 2, currentPage = 1))
        assertNull(pagerSyncTarget(currentIndex = 0, pageCount = 1, currentPage = 0))
    }
}
