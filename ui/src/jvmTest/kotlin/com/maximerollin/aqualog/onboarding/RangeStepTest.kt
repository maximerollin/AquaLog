package com.maximerollin.aqualog.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The range sheet's steppers and the drag threshold of a Parameter row (lot 5, Task 4). */
class RangeStepTest {
    @Test
    fun `plus adds one step at the precision`() {
        assertEquals("6,6", stepRangeInput("6,5", 1, 1, ','))
        assertEquals("0,11", stepRangeInput("0,1", 2, 1, ','))
    }

    @Test
    fun `input is rounded to the precision before stepping`() {
        assertEquals("7", stepRangeInput("6,5", 0, 1, ','))
        assertEquals("6", stepRangeInput("6,5", 0, -1, ','))
    }

    @Test
    fun `minus clamps at zero`() {
        assertEquals("0,0", stepRangeInput("0", 1, -1, ','))
        assertEquals("0,0", stepRangeInput("0,05", 1, -1, ','))
    }

    @Test
    fun `blank starts from zero`() {
        assertEquals("1", stepRangeInput("", 0, 1, ','))
    }

    @Test
    fun `unparsable input does nothing`() {
        assertNull(stepRangeInput("abc", 1, 1, ','))
    }

    @Test
    fun `no floating error`() {
        assertEquals("0.8", stepRangeInput("0,7", 1, 1, '.'))
        var value = "0"
        repeat(10) { value = stepRangeInput(value, 1, 1, '.')!! }
        assertEquals("1.0", value)
    }

    @Test
    fun `precision stays within 0 and 3`() {
        assertEquals("3", stepPrecision("3", 1))
        assertEquals("0", stepPrecision("0", -1))
        assertEquals("1", stepPrecision("x", 1))
    }

    @Test
    fun `reorder waits for half a neighbour`() {
        assertEquals(0, reorderOffset(-29f, 60f, 60f))
        assertEquals(-1, reorderOffset(-31f, 60f, 60f))
        assertEquals(0, reorderOffset(31f, 60f, null))
        assertEquals(1, reorderOffset(31f, null, 60f))
    }
}
