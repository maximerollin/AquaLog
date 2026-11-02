package com.maximerollin.aqualog.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ScenePhysicsTest {
    @Test
    fun plantSwayEasesInAndOutOfTheAmplitude() {
        assertEquals(0f, plantSway(0f), 1e-4f)
        assertEquals(4f, plantSway(0.5f), 1e-4f)
        assertEquals(0f, plantSway(1f), 1e-4f)
    }

    @Test
    fun bubbleRisesLinearlyAndFadesOut() {
        assertEquals(0f to 1f, bubbleRise(0f, 140f))
        assertEquals(-140f to 0f, bubbleRise(1f, 140f))
    }

    @Test
    fun cycleProgressLoopsOverItsPeriod() {
        assertEquals(0f, cycleProgress(0, 4_000))
        assertEquals(.25f, cycleProgress(1_000, 4_000))
        assertEquals(0f, cycleProgress(4_000, 4_000))
        assertEquals(.5f, cycleProgress(10_000, 4_000))
    }
}
