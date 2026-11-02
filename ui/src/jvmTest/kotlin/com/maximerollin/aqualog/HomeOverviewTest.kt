package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RecordedSession
import com.maximerollin.aqualog.shared.Session
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.VolumeUnit
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
        position = position, unit = "", precision = 1, indicativeMinimum = min, indicativeMaximum = max,
    )

    private fun setup(vararg definitions: ParameterDefinition) = AquariumSetup(aquarium(), definitions.toList())

    private fun reading(definition: ParameterDefinition, value: Double, at: Long = 1_000 * day) =
        LatestReading(definition.id, value, at)

    private fun session(at: Long) = RecordedSession(Session("s1", "a1", at, at), emptyList(), emptyList(), emptyList())

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
            null,
        )
        assertEquals(listOf(ph, nitrite, nitrate), overview.values.map { it.definition })
        assertEquals(listOf(false, true, false), overview.values.map { it.isOutsideRange })
        assertEquals(1, overview.outsideRangeCount)
    }

    @Test
    fun `never measured parameter shows no value and no attention`() {
        val gh = definition(BuiltInParameter.GH, 0, 4.0, 8.0)
        val value = homeOverview(setup(gh), emptyList(), null).values.single()
        assertNull(value.value)
        assertNull(value.occurredAtEpochMillis)
        assertFalse(value.isOutsideRange)
    }

    @Test
    fun `parameter without range is never out of range`() {
        val tds = definition(BuiltInParameter.TDS, 0)
        val value = homeOverview(setup(tds), listOf(reading(tds, 900.0)), null).values.single()
        assertFalse(value.isOutsideRange)
    }

    @Test
    fun `inactive parameters are left out`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val gh = definition(BuiltInParameter.GH, 1, active = false)
        assertEquals(listOf(ph), homeOverview(setup(ph, gh), emptyList(), null).values.map { it.definition })
    }

    @Test
    fun `more than six active parameters keep the out of range ones`() {
        val definitions = BuiltInParameter.entries.take(8).mapIndexed { index, parameter ->
            definition(parameter, index, 0.0, 10.0)
        }
        val readings = definitions.mapIndexed { index, d -> reading(d, if (index == 7) 50.0 else 5.0) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), readings, null)
        assertEquals(6, overview.values.size)
        assertTrue(definitions[7] in overview.values.map { it.definition })
        assertEquals(overview.values.map { it.definition.position }.sorted(), overview.values.map { it.definition.position })
        assertEquals(listOf(0, 1, 2, 3, 4, 7), overview.values.map { it.definition.position })
    }

    @Test
    fun `never measured parameters are trimmed before measured ones`() {
        val definitions = BuiltInParameter.entries.take(7).mapIndexed { index, parameter -> definition(parameter, index) }
        val readings = definitions.drop(1).map { reading(it, 1.0) }
        val overview = homeOverview(setup(*definitions.toTypedArray()), readings, null)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), overview.values.map { it.definition.position })
    }

    @Test
    fun `older reading is flagged against the latest session`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val kh = definition(BuiltInParameter.KH, 1)
        val overview = homeOverview(
            setup(ph, kh),
            listOf(reading(ph, 7.0, 1_000 * day), reading(kh, 4.0, 988 * day)),
            session(1_000 * day),
        )
        assertEquals(listOf(false, true), overview.values.map { it.isOlderThanLatestSession })
        assertEquals(1_000 * day, overview.latestSessionAtEpochMillis)
    }

    @Test
    fun `no session yet`() {
        val ph = definition(BuiltInParameter.PH, 0)
        val overview = homeOverview(setup(ph), listOf(reading(ph, 7.0)), null)
        assertNull(overview.latestSessionAtEpochMillis)
        assertFalse(overview.values.single().isOlderThanLatestSession)
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
}
