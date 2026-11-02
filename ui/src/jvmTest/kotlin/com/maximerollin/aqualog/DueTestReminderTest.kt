package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.NEVER_INTERVAL_DAYS
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DueTestReminderTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = TaskDate(2026, 11, 2)

    private fun at(day: Int, hour: Int, month: Int = 11) =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun `due today before nine reminds today at nine`() {
        assertEquals(at(2, 9), nextDueTestReminder(listOf(today), paris, now = at(2, 7)))
    }

    @Test
    fun `due today after nine reminds tomorrow at nine`() {
        assertEquals(at(3, 9), nextDueTestReminder(listOf(today), paris, now = at(2, 10)))
    }

    @Test
    fun `overdue before nine reminds today, a future date at its own nine`() {
        assertEquals(at(2, 9), nextDueTestReminder(listOf(TaskDate(2026, 10, 28)), paris, now = at(2, 7)))
        assertEquals(at(5, 9), nextDueTestReminder(listOf(TaskDate(2026, 11, 5), TaskDate(2026, 11, 9)), paris, now = at(2, 10)))
    }

    @Test
    fun `nothing due means no reminder`() {
        assertNull(nextDueTestReminder(emptyList(), paris, now = at(2, 7)))
    }

    private val nitrate = ParameterDefinition(
        id = "nitrate", aquariumId = "a1", parameter = BuiltInParameter.NITRATE, isActive = true,
        position = 0, unit = "mg/L", precision = 1, indicativeMinimum = 0.0, indicativeMaximum = 40.0,
    )
    private fun setup(vararg parameters: ParameterDefinition) = AquariumSetup(
        Aquarium("a1", "Bac", 60.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED),
        parameters.toList(),
    )

    @Test
    fun `next due date is the day the rhythm falls due, or null when never`() {
        val measured = listOf(ReadingSample("nitrate", 10.0, TaskDate(2026, 10, 30)))
        assertEquals(TaskDate(2026, 11, 6), nextDueDate(setup(nitrate), measured, today))
        assertEquals(today, nextDueDate(setup(nitrate), emptyList(), today))
        assertNull(nextDueDate(setup(nitrate.copy(analysisIntervalDays = NEVER_INTERVAL_DAYS)), measured, today))
    }
}
