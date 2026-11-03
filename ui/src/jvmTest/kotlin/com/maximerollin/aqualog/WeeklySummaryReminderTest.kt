package com.maximerollin.aqualog

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklySummaryReminderTest {
    private val paris = ZoneId.of("Europe/Paris")

    // Sunday 2026-11-08, Wednesday 2026-11-04.
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun `Sunday before seven pm reminds the same evening`() {
        assertEquals(at(8, 19), nextSundaySummary(at(8, 18), paris))
    }

    @Test
    fun `Sunday after seven pm reminds next Sunday`() {
        assertEquals(at(15, 19), nextSundaySummary(at(8, 20), paris))
        // Fired right on time: the next one is a week later.
        assertEquals(at(15, 19), nextSundaySummary(at(8, 19), paris))
    }

    @Test
    fun `midweek reminds the coming Sunday`() {
        assertEquals(at(8, 19), nextSundaySummary(at(4, 10), paris))
    }
}
