package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RhythmTest {
    private val today = TaskDate(2026, 11, 2)

    private fun definition(
        parameter: BuiltInParameter,
        interval: Int? = null,
        active: Boolean = true,
        min: Double? = null,
        max: Double? = null,
    ) = ParameterDefinition(
        id = "def-${parameter.storageValue}",
        aquariumId = "aq",
        parameter = parameter,
        isActive = active,
        position = BuiltInParameter.entries.indexOf(parameter),
        unit = OnboardingPresets.presetUnit(parameter),
        precision = 1,
        indicativeMinimum = min,
        indicativeMaximum = max,
        analysisIntervalDays = interval,
    )

    private fun setup(profile: AquariumProfile, vararg definitions: ParameterDefinition) = AquariumSetup(
        Aquarium("aq", "Bac", 100.0, VolumeUnit.LITERS, 0L, profile),
        definitions.toList(),
    )

    private fun sample(parameter: BuiltInParameter, value: Double, daysAgo: Int) =
        ReadingSample("def-${parameter.storageValue}", value, today.plusDays(-daysAgo))

    @Test
    fun `established nitrate measured 8 days ago is due`() {
        val due = dueTests(
            setup(AquariumProfile.ESTABLISHED, definition(BuiltInParameter.NITRATE)),
            listOf(sample(BuiltInParameter.NITRATE, 20.0, 8)),
            today,
        )
        assertEquals(listOf(DueReason.RHYTHM), due.map { it.reason })
        assertEquals(today.plusDays(-1), due.single().dueDate)
    }

    @Test
    fun `nitrite 13 days ago is not due`() {
        val due = dueTests(
            setup(AquariumProfile.ESTABLISHED, definition(BuiltInParameter.NITRITE)),
            listOf(sample(BuiltInParameter.NITRITE, 0.0, 13)),
            today,
        )
        assertTrue(due.isEmpty())
    }

    @Test
    fun `never measured active pH is due today`() {
        val due = dueTests(setup(AquariumProfile.ESTABLISHED, definition(BuiltInParameter.PH)), emptyList(), today)
        assertEquals(today, due.single().dueDate)
        assertEquals(DueReason.RHYTHM, due.single().reason)
    }

    @Test
    fun `custom interval 3 wins`() {
        val ph = definition(BuiltInParameter.PH, interval = 3)
        assertEquals(3, effectiveIntervalDays(ph, AquariumProfile.ESTABLISHED))
        val due = dueTests(setup(AquariumProfile.ESTABLISHED, ph), listOf(sample(BuiltInParameter.PH, 7.0, 3)), today)
        assertEquals(1, due.size)
    }

    @Test
    fun `never sentinel is never due`() {
        val ph = definition(BuiltInParameter.PH, interval = NEVER_INTERVAL_DAYS)
        assertNull(effectiveIntervalDays(ph, AquariumProfile.ESTABLISHED))
        assertTrue(dueTests(setup(AquariumProfile.ESTABLISHED, ph), emptyList(), today).isEmpty())
    }

    @Test
    fun `inactive parameters are never due`() {
        val ph = definition(BuiltInParameter.PH, active = false)
        assertTrue(dueTests(setup(AquariumProfile.ESTABLISHED, ph), emptyList(), today).isEmpty())
    }

    @Test
    fun `default intervals follow the rule table`() {
        assertEquals(7, defaultIntervalDays(AquariumProfile.ESTABLISHED, BuiltInParameter.NITRATE))
        assertEquals(2, defaultIntervalDays(AquariumProfile.CYCLING, BuiltInParameter.AMMONIA))
        assertEquals(7, defaultIntervalDays(AquariumProfile.PLANTED_SHRIMP, BuiltInParameter.TDS))
        assertEquals(7, defaultIntervalDays(AquariumProfile.PLANTED_SHRIMP, BuiltInParameter.GH))
        assertNull(defaultIntervalDays(AquariumProfile.ESTABLISHED, BuiltInParameter.AMMONIA))
        assertNull(defaultIntervalDays(AquariumProfile.ESTABLISHED, BuiltInParameter.TEMPERATURE))
    }

    @Test
    fun `an ALERT sample yesterday makes nitrite and ammonia follow-ups due today`() {
        val ph = definition(BuiltInParameter.PH, min = 6.5, max = 7.5)
        val nitrite = definition(BuiltInParameter.NITRITE)
        val ammonia = definition(BuiltInParameter.AMMONIA)
        val due = dueTests(
            setup(AquariumProfile.ESTABLISHED, ph, ammonia, nitrite),
            listOf(sample(BuiltInParameter.PH, 8.6, 1), sample(BuiltInParameter.NITRITE, 0.0, 1)),
            today,
        )
        val ammoniaDue = due.single { it.definition.parameter == BuiltInParameter.AMMONIA }
        assertEquals(DueReason.FOLLOW_UP, ammoniaDue.reason)
        assertEquals(today, ammoniaDue.dueDate)
        // Nitrite measured on T itself does not satisfy the T+1 follow-up.
        assertEquals(1, due.count { it.definition.parameter == BuiltInParameter.NITRITE })
    }

    @Test
    fun `a nitrite reading after the follow-up date clears it`() {
        val ph = definition(BuiltInParameter.PH, min = 6.5, max = 7.5, interval = NEVER_INTERVAL_DAYS)
        val nitrite = definition(BuiltInParameter.NITRITE)
        val samples = listOf(sample(BuiltInParameter.PH, 8.6, 5), sample(BuiltInParameter.NITRITE, 0.0, 1))
        // T = 5 days ago: follow-ups at T+1 (4 ago) and T+3 (2 ago); the reading 1 day ago covers both.
        assertTrue(dueTests(setup(AquariumProfile.ESTABLISHED, ph, nitrite), samples, today).isEmpty())
        // Without it, the earliest follow-up is reported.
        val due = dueTests(setup(AquariumProfile.ESTABLISHED, ph, nitrite), samples.take(1), today).single()
        assertEquals(today.plusDays(-4), due.dueDate)
    }

    @Test
    fun `while cycling daily out-of-range nitrite only follows the rhythm`() {
        val nitrite = definition(BuiltInParameter.NITRITE, min = 0.0, max = 0.0)
        val samples = (0..6).map { sample(BuiltInParameter.NITRITE, 1.0, it) }
        val cycling = setup(AquariumProfile.CYCLING, nitrite)
        assertTrue(dueTests(cycling, samples, today).isEmpty())
        val inTwoDays = dueTests(cycling, samples, today.plusDays(2)).single()
        assertEquals(DueReason.RHYTHM, inTwoDays.reason)
        assertEquals(today.plusDays(2), inTwoDays.dueDate)
        assertTrue((0..6).none { day -> dueTests(cycling, samples.drop(1), today.plusDays(day)).any { it.reason == DueReason.FOLLOW_UP } })
    }

    @Test
    fun `a chronic pH in ATTENTION does not bring nitrite follow-ups`() {
        val ph = definition(BuiltInParameter.PH, min = 6.5, max = 7.5)
        val nitrite = definition(BuiltInParameter.NITRITE)
        val samples = listOf(sample(BuiltInParameter.PH, 7.8, 1), sample(BuiltInParameter.NITRITE, 0.0, 1))
        val established = setup(AquariumProfile.ESTABLISHED, ph, nitrite)
        assertTrue((0..5).none { day -> dueTests(established, samples, today.plusDays(day)).any { it.definition == nitrite } })
    }

    @Test
    fun `nitrite in ATTENTION brings follow-ups, but never when set to never`() {
        val nitrite = definition(BuiltInParameter.NITRITE, min = 0.0, max = 0.0)
        val due = dueTests(setup(AquariumProfile.ESTABLISHED, nitrite), listOf(sample(BuiltInParameter.NITRITE, 0.25, 1)), today).single()
        assertEquals(DueReason.FOLLOW_UP, due.reason)
        assertEquals(today, due.dueDate)
        val never = nitrite.copy(analysisIntervalDays = NEVER_INTERVAL_DAYS)
        assertTrue((0..7).all { dueTests(setup(AquariumProfile.ESTABLISHED, never), listOf(sample(BuiltInParameter.NITRITE, 0.25, 1)), today.plusDays(it)).isEmpty() })
    }

    @Test
    fun `an out-of-range reading is due again at the pistes' retest day until a later reading`() {
        val nitrate = definition(BuiltInParameter.NITRATE, min = 0.0, max = 40.0)
        val high = listOf(sample(BuiltInParameter.NITRATE, 60.0, 0))
        val established = setup(AquariumProfile.ESTABLISHED, nitrate)
        assertTrue(dueTests(established, high, today.plusDays(2)).isEmpty())
        val retest = dueTests(established, high, today.plusDays(3)).single()
        assertEquals(DueReason.FOLLOW_UP, retest.reason)
        assertEquals(today.plusDays(3), retest.dueDate)
        // A later in-range reading clears it; the rhythm is back to 7 days.
        val cleared = high + sample(BuiltInParameter.NITRATE, 20.0, -1)
        assertTrue(dueTests(established, cleared, today.plusDays(3)).isEmpty())
    }

    @Test
    fun `due tests are earliest first, one per parameter`() {
        val ph = definition(BuiltInParameter.PH)
        val nitrate = definition(BuiltInParameter.NITRATE)
        val due = dueTests(
            setup(AquariumProfile.ESTABLISHED, ph, nitrate),
            listOf(sample(BuiltInParameter.PH, 7.0, 10), sample(BuiltInParameter.NITRATE, 5.0, 20)),
            today,
        )
        assertEquals(listOf(BuiltInParameter.NITRATE, BuiltInParameter.PH), due.map { it.definition.parameter })
    }

    @Test
    fun `cycling looks cycled when ammonia and nitrite are 0`() {
        val cycling = setup(AquariumProfile.CYCLING, definition(BuiltInParameter.AMMONIA), definition(BuiltInParameter.NITRITE))
        fun latest(ammonia: Double?, nitrite: Double?) = listOfNotNull(
            ammonia?.let { LatestReading("def-ammonia", it, 0L) },
            nitrite?.let { LatestReading("def-nitrite", it, 0L) },
        )
        assertTrue(looksCycled(cycling, latest(0.0, 0.0)))
        assertFalse(looksCycled(cycling, latest(0.0, 0.25)))
        assertFalse(looksCycled(cycling, latest(0.0, null)))
        assertFalse(looksCycled(setup(AquariumProfile.ESTABLISHED, definition(BuiltInParameter.NITRITE)), latest(null, 0.0)))
        assertFalse(looksCycled(setup(AquariumProfile.CYCLING, definition(BuiltInParameter.PH)), emptyList()))
    }

    @Test
    fun `epoch day round-trips across leap years and month ends`() {
        assertEquals(0L, TaskDate(1970, 1, 1).toEpochDay())
        for (date in listOf(TaskDate(2024, 2, 29), TaskDate(2026, 12, 31), TaskDate(2026, 11, 2), TaskDate(1999, 3, 1))) {
            assertEquals(date, TaskDate.fromEpochDay(date.toEpochDay()))
        }
        assertEquals(TaskDate(2027, 1, 1), TaskDate(2026, 12, 31).plusDays(1))
        assertEquals(TaskDate(2026, 12, 31), TaskDate(2027, 1, 1).plusDays(-1))
    }

    private val ammonia = definition(BuiltInParameter.AMMONIA, min = 0.0, max = 0.0)
    private val nitriteAt0 = definition(BuiltInParameter.NITRITE, min = 0.0, max = 0.0)

    @Test
    fun `adding an inhabitant schedules ammonia and nitrite at D+1 and D+3`() {
        val established = setup(AquariumProfile.ESTABLISHED, ammonia, nitriteAt0)
        val added = listOf(today)
        val tested = listOf(sample(BuiltInParameter.AMMONIA, 0.0, 0), sample(BuiltInParameter.NITRITE, 0.0, 0))
        assertTrue(dueTests(established, tested, today, added).isEmpty())
        val tomorrow = dueTests(established, tested, today.plusDays(1), added)
        assertEquals(listOf(ammonia, nitriteAt0).toSet(), tomorrow.map { it.definition }.toSet())
        assertTrue(tomorrow.all { it.reason == DueReason.FOLLOW_UP && it.dueDate == today.plusDays(1) })
        // Tested on D+2 (today): D+1 is cleared, D+3 is next.
        assertTrue(dueTests(established, tested, today, listOf(today.plusDays(-2))).isEmpty())
        val third = dueTests(established, tested, today.plusDays(1), listOf(today.plusDays(-2)))
        assertEquals(2, third.size)
        assertTrue(third.all { it.reason == DueReason.FOLLOW_UP && it.dueDate == today.plusDays(1) })
    }

    @Test
    fun `no follow-up while cycling`() {
        val cycling = setup(AquariumProfile.CYCLING, ammonia, nitriteAt0)
        val samples = listOf(sample(BuiltInParameter.AMMONIA, 0.0, 0), sample(BuiltInParameter.NITRITE, 0.0, 0))
        assertTrue((0..1).none { day ->
            dueTests(cycling, samples, today.plusDays(day), listOf(today)).any { it.reason == DueReason.FOLLOW_UP }
        })
    }

    @Test
    fun `a measurement after D+1 clears that follow-up`() {
        val established = setup(AquariumProfile.ESTABLISHED, ammonia, nitriteAt0)
        val added = listOf(today.plusDays(-1))
        // Tested 3 days ago: the rhythm is not due, only the D+1 follow-up is.
        val old = listOf(sample(BuiltInParameter.AMMONIA, 0.0, 3), sample(BuiltInParameter.NITRITE, 0.0, 3))
        val before = dueTests(established, old, today, added)
        assertEquals(2, before.size)
        assertTrue(before.all { it.reason == DueReason.FOLLOW_UP })
        val tested = old + listOf(sample(BuiltInParameter.AMMONIA, 0.0, 0), sample(BuiltInParameter.NITRITE, 0.0, 0))
        assertTrue(dueTests(established, tested, today, added).isEmpty())
    }
}
