package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeOverviewTest {
    private val day = 86_400_000L
    private val today = TaskDate(2026, 11, 2)
    private val yesterday = TaskDate(2026, 11, 1)
    private val tomorrow = TaskDate(2026, 11, 3)

    private fun aquarium(id: String = "a1", archived: Boolean = false) =
        Aquarium(id, "Aquarium $id", 100.0, VolumeUnit.LITERS, 0L, isArchived = archived)

    private fun definition(
        parameter: BuiltInParameter,
        position: Int,
        min: Double? = null,
        max: Double? = null,
        active: Boolean = true,
    ) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = active,
        position = position, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private fun setup(vararg definitions: ParameterDefinition) = AquariumSetup(aquarium(), definitions.toList())

    private fun reading(definition: ParameterDefinition, value: Double, at: Long = 1_000 * day, previous: Double? = null) =
        LatestReading(definition.id, value, at, previous)

    private fun occurrence(id: String, aquariumId: String, due: TaskDate) =
        TaskOccurrence(id, "t-$id", aquariumId, "Task $id", due, null, "UTC", null, null)

    @Test
    fun `values keep definition order and mark out of range`() {
        val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
        val nitrite = definition(BuiltInParameter.NITRITE, 1, 0.0, 0.0)
        val nitrate = definition(BuiltInParameter.NITRATE, 2, 0.0, 40.0)
        val overview = homeOverview(
            setup(nitrate, ph, nitrite),
            listOf(reading(ph, 7.2), reading(nitrite, 0.25), reading(nitrate, 20.0)),
        )
        assertEquals(listOf(ph, nitrite, nitrate), overview.values.map { it.definition })
        assertEquals(listOf(ReadingLevel.OK, ReadingLevel.ATTENTION, ReadingLevel.OK), overview.values.map { it.level })
        assertEquals(1, overview.outsideRangeCount)
    }

    @Test
    fun `nitrite 0_6 is ALERT and counts as out of range`() {
        val nitrite = definition(BuiltInParameter.NITRITE, 0, 0.0, 0.0)
        val overview = homeOverview(setup(nitrite), listOf(reading(nitrite, 0.6)))
        assertEquals(ReadingLevel.ALERT, overview.values.single().level)
        assertEquals(1, overview.outsideRangeCount)
    }

    @Test
    fun `never measured parameter shows no value and no attention`() {
        val gh = definition(BuiltInParameter.GH, 0, 4.0, 8.0)
        val value = homeOverview(setup(gh), emptyList()).values.single()
        assertNull(value.value)
        assertNull(value.occurredAtEpochMillis)
        assertEquals(ReadingLevel.OK, value.level)
    }

    @Test
    fun `parameter without range is never out of range`() {
        val tds = definition(BuiltInParameter.TDS, 0)
        val value = homeOverview(setup(tds), listOf(reading(tds, 500.0))).values.single()
        assertEquals(ReadingLevel.OK, value.level)
    }

    @Test
    fun `inactive parameters are left out`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val gh = definition(BuiltInParameter.GH, 1, active = false)
        assertEquals(listOf(ph), homeOverview(setup(ph, gh), emptyList()).values.map { it.definition })
    }

    @Test
    fun `more than six active parameters keep the out of range ones`() {
        val definitions = BuiltInParameter.entries.take(8).mapIndexed { index, parameter ->
            definition(parameter, index, 0.0, 30.0)
        }
        val readings = definitions.mapIndexed { index, d -> reading(d, if (index == 7) 50.0 else 5.0) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), readings)
        assertEquals(6, overview.values.size)
        assertTrue(definitions[7] in overview.values.map { it.definition })
        assertEquals(overview.values.map { it.definition.position }.sorted(), overview.values.map { it.definition.position })
        assertEquals(listOf(0, 1, 2, 3, 4, 7), overview.values.map { it.definition.position })
    }

    @Test
    fun `outside range count covers every active parameter, not only the six shown`() {
        val definitions = BuiltInParameter.entries.take(8).mapIndexed { index, parameter ->
            definition(parameter, index, 0.0, 30.0)
        }
        val readings = definitions.mapIndexed { index, d -> reading(d, if (index == 0) 20.0 else 50.0) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), readings)
        assertEquals(6, overview.values.size)
        assertEquals(7, overview.outsideRangeCount)
    }

    @Test
    fun `never measured parameters are trimmed before measured ones`() {
        val definitions = BuiltInParameter.entries.take(7).mapIndexed { index, parameter -> definition(parameter, index) }
        val readings = definitions.drop(1).map { reading(it, 1.0) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), readings)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), overview.values.map { it.definition.position })
    }

    @Test
    fun `older reading is flagged against the newest reading`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val kh = definition(BuiltInParameter.KH, 1)
        val overview = homeOverview(
            setup(ph, kh),
            listOf(reading(ph, 7.0, 1_000 * day), reading(kh, 4.0, 988 * day)),
        )
        assertEquals(listOf(false, true), overview.values.map { it.isOlderThanLatestReading })
        assertEquals(1_000 * day, overview.latestReadingAtEpochMillis)
    }

    @Test
    fun `a maintenance only Session does not move the reference time`() {
        // The latest Session (day 1 005) only changed water: readings are what « measured … » is about.
        val ph = definition(BuiltInParameter.PH, 0)
        val kh = definition(BuiltInParameter.KH, 1)
        val overview = homeOverview(setup(ph, kh), listOf(reading(ph, 7.0, 1_000 * day), reading(kh, 4.0, 1_000 * day)))
        assertEquals(1_000 * day, overview.latestReadingAtEpochMillis)
        assertEquals(listOf(false, false), overview.values.map { it.isOlderThanLatestReading })
    }

    @Test
    fun `inactive parameters do not set the reference time`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val gh = definition(BuiltInParameter.GH, 1, active = false)
        val overview = homeOverview(setup(ph, gh), listOf(reading(ph, 7.0, 990 * day), reading(gh, 6.0, 1_000 * day)))
        assertEquals(990 * day, overview.latestReadingAtEpochMillis)
        assertFalse(overview.values.single().isOlderThanLatestReading)
    }

    @Test
    fun `no reading yet`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val overview = homeOverview(setup(ph), emptyList())
        assertNull(overview.latestReadingAtEpochMillis)
        assertFalse(overview.values.single().isOlderThanLatestReading)
    }

    @Test
    fun `trend compares with the previous reading at the parameter precision`() {
        val ph = definition(BuiltInParameter.PH, 0) // precision 1
        val kh = definition(BuiltInParameter.KH, 1)
        val gh = definition(BuiltInParameter.GH, 2)
        val no2 = definition(BuiltInParameter.NITRITE, 3)
        val no3 = definition(BuiltInParameter.NITRATE, 4)
        val overview = homeOverview(
            setup(ph, kh, gh, no2, no3),
            listOf(
                reading(ph, 7.2, previous = 7.0),
                reading(kh, 4.0, previous = 5.0),
                reading(gh, 6.04, previous = 6.0),
                reading(no2, 0.1, previous = 0.0),
                reading(no3, 20.0),
            ),
        )
        assertEquals(listOf(Trend.UP, Trend.DOWN, Trend.FLAT, Trend.UP, null), overview.values.map { it.trend })
    }

    @Test
    fun `trend threshold is half a displayed step`() {
        assertEquals(Trend.FLAT, trend(7.24, 7.2, 1))
        assertEquals(Trend.UP, trend(7.26, 7.2, 1))
        assertEquals(Trend.DOWN, trend(24.0, 25.0, 0))
        assertEquals(Trend.FLAT, trend(24.4, 24.0, 0))
    }

    @Test
    fun `today tasks keep due and overdue of this aquarium only`() {
        val pending = listOf(
            occurrence("1", "a1", yesterday),
            occurrence("2", "a2", yesterday),
            occurrence("3", "a1", today),
            occurrence("4", "a1", tomorrow),
        )
        val result = todayTasks(pending, "a1", today)
        assertEquals(listOf("1", "3"), result.due.map { it.id })
        assertNull(result.next)
    }

    @Test
    fun `overdue linked action only counts an overdue Occurrence of that action and Aquarium`() {
        val wc = MaintenanceActionType.WATER_CHANGE
        val pending = listOf(
            occurrence("1", "a1", yesterday).copy(maintenanceActionType = wc),
            occurrence("2", "a2", yesterday).copy(maintenanceActionType = wc),
            occurrence("3", "a1", today).copy(maintenanceActionType = MaintenanceActionType.VACUUM),
        )
        assertTrue(overdueLinkedAction(pending, "a1", wc, today))
        assertFalse(overdueLinkedAction(pending, "a1", MaintenanceActionType.VACUUM, today))
        assertFalse(overdueLinkedAction(pending.drop(1), "a1", wc, today))
    }

    @Test
    fun `default maintenance input reuses the last quantity else the built-in default`() {
        assertEquals(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"), defaultMaintenanceInput(MaintenanceActionType.WATER_CHANGE, null))
        assertEquals(MaintenanceActionInput(MaintenanceActionType.VACUUM), defaultMaintenanceInput(MaintenanceActionType.VACUUM, null))
        val last = MaintenanceAction("m", "s", MaintenanceActionType.WATER_CHANGE, 35.0, "%", null)
        assertEquals(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "35", "%"), defaultMaintenanceInput(MaintenanceActionType.WATER_CHANGE, last))
    }

    @Test
    fun `today tasks keep at most three`() {
        val pending = (1..5).map { occurrence("$it", "a1", today) }
        assertEquals(listOf("1", "2", "3"), todayTasks(pending, "a1", today).due.map { it.id })
    }

    @Test
    fun `today tasks offer the next one when nothing is due`() {
        val pending = listOf(
            occurrence("1", "a2", tomorrow),
            occurrence("2", "a1", tomorrow),
            occurrence("3", "a1", TaskDate(2026, 11, 9)),
        )
        val result = todayTasks(pending, "a1", today)
        assertTrue(result.due.isEmpty())
        assertEquals("2", result.next?.id)
    }

    @Test
    fun `alerts list other aquariums with something due today or overdue`() {
        val aquariums = listOf(aquarium("a1"), aquarium("a2"), aquarium("a3", archived = true), aquarium("a4"), aquarium("a5"))
        val pending = listOf(
            occurrence("1", "a1", yesterday),
            occurrence("2", "a2", yesterday),
            occurrence("3", "a2", today),
            occurrence("4", "a3", yesterday),
            occurrence("5", "a4", today),
            occurrence("6", "a5", tomorrow),
        )
        val alerts = otherAquariumAlerts(aquariums, "a1", pending, today)
        assertEquals(listOf("a2", "a4"), alerts.map { it.aquarium.id })
        assertEquals(listOf("2", "5"), alerts.map { it.occurrence.id })
        assertEquals(listOf(true, false), alerts.map { it.isOverdue })
    }

    @Test
    fun `alerts are counted, and capped at ATTENTION for ammonia and nitrite while cycling`() {
        val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
        val nitrite = definition(BuiltInParameter.NITRITE, 1, 0.0, 0.0)
        val readings = listOf(reading(ph, 8.6), reading(nitrite, 1.0))
        val established = homeOverview(setup(ph, nitrite), readings)
        assertEquals(2, established.alertCount)
        val cycling = homeOverview(AquariumSetup(aquarium().copy(profile = AquariumProfile.CYCLING), listOf(ph, nitrite)), readings)
        assertEquals(listOf(ReadingLevel.ALERT, ReadingLevel.ATTENTION), cycling.values.map { it.level })
        assertEquals(1, cycling.alertCount)
        assertEquals(2, cycling.outsideRangeCount)
    }

    @Test
    fun `all keeps every active parameter in position order beyond six`() {
        val definitions = BuiltInParameter.entries.take(8).mapIndexed { index, parameter -> definition(parameter, index) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), emptyList())
        assertEquals(8, overview.all.size)
        assertEquals((0..7).toList(), overview.all.map { it.definition.position })
        assertEquals(6, overview.values.size)
    }

    @Test
    fun `in range count is measured minus out of range`() {
        val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
        val gh = definition(BuiltInParameter.GH, 1, 4.0, 12.0)
        val kh = definition(BuiltInParameter.KH, 2, 3.0, 8.0)
        val overview = homeOverview(setup(ph, gh, kh), listOf(reading(ph, 7.8), reading(gh, 8.0)))
        assertEquals(2, overview.measuredCount)
        assertEquals(1, overview.inRangeCount)
    }
}
