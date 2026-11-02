package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesTest {
    // Wednesday; the current week starts Monday 2026-11-02, the last finished weeks Oct 26, Oct 19, Oct 12.
    private val today = TaskDate(2026, 11, 4)
    private val created = java.time.LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun day(month: Int, day: Int) = TaskDate(2026, month, day)

    private fun definition(aquariumId: String, parameter: BuiltInParameter, interval: Int?) = ParameterDefinition(
        id = "$aquariumId-${parameter.storageValue}", aquariumId = aquariumId, parameter = parameter, isActive = true,
        position = 0, unit = "", precision = 1, indicativeMinimum = null, indicativeMaximum = null, analysisIntervalDays = interval,
    )

    /** Weekly nitrate (7 days) unless [interval] says otherwise; [readings] are the days nitrate was measured. */
    private fun history(
        readings: List<TaskDate> = emptyList(),
        occurrences: List<TaskOccurrence> = emptyList(),
        id: String = "aq",
        interval: Int? = 7,
    ): AquariumHistory {
        val nitrate = definition(id, BuiltInParameter.NITRATE, interval)
        return AquariumHistory(
            AquariumSetup(Aquarium(id, id, 100.0, VolumeUnit.LITERS, created, AquariumProfile.ESTABLISHED), listOf(nitrate)),
            readings.map { ReadingSample(nitrate.id, 10.0, it) },
            occurrences,
        )
    }

    private fun occurrence(due: TaskDate, resolution: TaskResolution?, resolvedOn: TaskDate? = null, id: String = "aq") = TaskOccurrence(
        id = "o-$due", taskId = "t", aquariumId = id, title = "Water change", dueDate = due, minuteOfDay = null,
        timeZoneId = "UTC", resolution = resolution,
        resolvedAtEpochMillis = resolvedOn?.let { it.toEpochDay() * 86_400_000L + 36_000_000L },
    )

    @Test
    fun `three kept weeks give 3`() {
        val readings = listOf(day(10, 13), day(10, 20), day(10, 27))
        assertEquals(3, weeklySeries(listOf(history(readings)), today))
    }

    @Test
    fun `a missed week breaks`() {
        val readings = listOf(day(10, 13), day(10, 27))
        assertEquals(1, weeklySeries(listOf(history(readings)), today))
    }

    @Test
    fun `a neutral week is skipped`() {
        // No rhythm shorter than a week: only the Tâches make requirements, and none is due the week of Oct 19.
        val done = listOf(
            occurrence(day(10, 14), TaskResolution.COMPLETED, day(10, 14)),
            occurrence(day(10, 28), TaskResolution.COMPLETED, day(10, 28)),
        )
        assertEquals(2, weeklySeries(listOf(history(occurrences = done, interval = 30)), today))
    }

    @Test
    fun `a reading on Monday after the week still counts`() {
        assertEquals(2, weeklySeries(listOf(history(listOf(day(10, 26)))), today))
        assertEquals(1, weeklySeries(listOf(history(listOf(day(10, 27)))), today))
    }

    @Test
    fun `an ignored task breaks the week`() {
        val readings = listOf(day(10, 13), day(10, 20), day(10, 27))
        val ignored = listOf(occurrence(day(10, 21), TaskResolution.IGNORED, day(10, 21)))
        assertEquals(1, weeklySeries(listOf(history(readings, ignored)), today))
    }

    @Test
    fun `a task completed after the grace day or left pending breaks the week`() {
        val readings = listOf(day(10, 13), day(10, 20), day(10, 27))
        val late = listOf(occurrence(day(10, 21), TaskResolution.COMPLETED, day(10, 27)))
        val pending = listOf(occurrence(day(10, 21), null))
        val onTime = listOf(occurrence(day(10, 21), TaskResolution.COMPLETED, day(10, 26)))
        assertEquals(1, weeklySeries(listOf(history(readings, late)), today))
        assertEquals(1, weeklySeries(listOf(history(readings, pending)), today))
        assertEquals(3, weeklySeries(listOf(history(readings, onTime)), today))
    }

    @Test
    fun `a postponed occurrence does not count itself`() {
        val readings = listOf(day(10, 13), day(10, 20), day(10, 27))
        val postponed = listOf(occurrence(day(10, 21), TaskResolution.POSTPONED, day(10, 21)))
        assertEquals(3, weeklySeries(listOf(history(readings, postponed)), today))
    }

    @Test
    fun `two aquariums - one missing breaks`() {
        val every = listOf(day(10, 13), day(10, 20), day(10, 27))
        val gap = listOf(day(10, 13), day(10, 27))
        assertEquals(1, weeklySeries(listOf(history(every, id = "a"), history(gap, id = "b")), today))
        assertEquals(3, weeklySeries(listOf(history(every, id = "a"), history(every, id = "b")), today))
    }

    @Test
    fun `current week met adds one, unmet does not break`() {
        val finished = listOf(day(10, 13), day(10, 20), day(10, 27))
        assertEquals(3, weeklySeries(listOf(history(finished)), today))
        assertEquals(4, weeklySeries(listOf(history(finished + day(11, 3))), today))
    }

    @Test
    fun `weeks before the Aquarium existed are neutral`() {
        val brandNew = history(listOf(day(11, 3))).let {
            it.copy(setup = it.setup.copy(aquarium = it.setup.aquarium.copy(
                createdAtEpochMillis = java.time.LocalDate.of(2026, 10, 27).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            )))
        }
        // Created the week of Oct 26, kept; the current week is met too.
        assertEquals(2, weeklySeries(listOf(brandNew.copy(samples = brandNew.samples + ReadingSample("aq-nitrate", 10.0, day(10, 28)))), today))
    }

    @Test
    fun `no history gives 0`() {
        assertEquals(0, weeklySeries(emptyList(), today))
    }
}
