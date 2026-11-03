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
import kotlin.test.assertNull

class WeeklySummaryTest {
    // The week Monday 2026-10-26 to Sunday 2026-11-01.
    private val weekStart = TaskDate(2026, 10, 26)

    private fun day(month: Int, day: Int) = TaskDate(2026, month, day)

    private fun definition(parameter: BuiltInParameter, position: Int, precision: Int, isActive: Boolean = true) = ParameterDefinition(
        id = parameter.storageValue, aquariumId = "aq", parameter = parameter, isActive = isActive, position = position,
        unit = "", precision = precision, indicativeMinimum = null, indicativeMaximum = null, analysisIntervalDays = null,
    )

    private val ph = definition(BuiltInParameter.PH, 0, precision = 1)
    private val nitrate = definition(BuiltInParameter.NITRATE, 1, precision = 0)
    private val gh = definition(BuiltInParameter.GH, 2, precision = 0)
    private val kh = definition(BuiltInParameter.KH, 3, precision = 0)
    private val nitrite = definition(BuiltInParameter.NITRITE, 4, precision = 2, isActive = false)

    private fun sample(definition: ParameterDefinition, value: Double, day: TaskDate) = ReadingSample(definition.id, value, day)

    private fun occurrence(id: String, resolution: TaskResolution?, resolvedOn: TaskDate?) = TaskOccurrence(
        id = id, taskId = "t", aquariumId = "aq", title = "Water change", dueDate = day(10, 20), minuteOfDay = null,
        timeZoneId = "UTC", resolution = resolution,
        resolvedAtEpochMillis = resolvedOn?.let { it.toEpochDay() * 86_400_000L + 36_000_000L },
    )

    // Newest first, as the repository gives them.
    private val samples = listOf(
        sample(ph, 7.3, day(11, 2)), // after the week
        sample(nitrate, 35.0, day(10, 31)),
        sample(gh, 8.4, day(10, 29)),
        sample(ph, 7.0, day(10, 28)),
        sample(nitrate, 30.0, day(10, 27)),
        sample(kh, 5.0, day(10, 27)),
        sample(nitrate, 20.0, day(10, 20)),
        sample(ph, 7.1, day(10, 19)),
        sample(gh, 8.0, day(10, 19)),
        sample(nitrite, 0.0, day(10, 19)),
    )

    private val history = AquariumHistory(
        AquariumSetup(
            Aquarium("aq", "Amazonien", 100.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED),
            listOf(ph, nitrate, gh, kh, nitrite),
        ),
        samples,
        listOf(
            occurrence("done", TaskResolution.COMPLETED, day(10, 28)),
            occurrence("done-after", TaskResolution.COMPLETED, day(11, 2)),
            occurrence("ignored", TaskResolution.IGNORED, day(10, 29)),
            occurrence("pending", null, null),
        ),
    )

    @Test
    fun `counts the readings and the tasks completed in the week`() {
        val summary = weeklySummary(history, weekStart, series = 3)
        assertEquals(5, summary.testsDone)
        assertEquals(1, summary.tasksCompleted)
        assertEquals(3, summary.series)
    }

    @Test
    fun `changes keep the last value of the week against the last one before, from one step of precision`() {
        // pH 7.1 -> 7.0 is exactly one step (0.1) despite floating point; GH 8 -> 8.4 is under one step; KH has no earlier value.
        assertEquals(
            listOf(ValueChange(ph, 7.1, 7.0), ValueChange(nitrate, 20.0, 35.0)),
            weeklySummary(history, weekStart, series = 0).changes,
        )
    }

    @Test
    fun `next week lists the tests due by the following Sunday`() {
        // Nitrate (every 7 days) was last read Oct 31: due Nov 7. pH was read Nov 2: due Nov 9. GH and KH every 30 days.
        assertEquals(listOf(nitrate), weeklySummary(history, weekStart, series = 0).nextWeek.map { it.definition })
    }

    @Test
    fun `the summary week is shown on Sunday and the next Monday only`() {
        assertEquals(weekStart, summaryWeekStart(day(11, 1)))
        assertEquals(weekStart, summaryWeekStart(day(11, 2)))
        assertNull(summaryWeekStart(day(11, 3)))
        assertNull(summaryWeekStart(day(10, 31)))
    }
}
