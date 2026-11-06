package com.maximerollin.aqualog.widget

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.DueReason
import com.maximerollin.aqualog.shared.assistance.DueTest
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.createJvmDatabaseBuilder
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetAquariumTest {
    private val today = TaskDate(2026, 11, 2)
    private val yesterday = TaskDate(2026, 11, 1)
    private val tomorrow = TaskDate(2026, 11, 3)

    private fun definition(parameter: BuiltInParameter, position: Int, min: Double? = null, max: Double? = null) =
        ParameterDefinition(
            id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
            position = position, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
            indicativeMinimum = min, indicativeMaximum = max,
        )

    private val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
    private val nitrite = definition(BuiltInParameter.NITRITE, 1, 0.0, 0.0)
    private val nitrate = definition(BuiltInParameter.NITRATE, 2, 0.0, 40.0)
    private val setup = AquariumSetup(Aquarium("a1", "Aquarium", 100.0, VolumeUnit.LITERS, 0L), listOf(ph, nitrite, nitrate))

    private fun reading(definition: ParameterDefinition, value: Double) = LatestReading(definition.id, value, 1_000L, null)

    private fun occurrence(id: String, due: TaskDate, aquariumId: String = "a1") =
        TaskOccurrence(id, "t-$id", aquariumId, "Task $id", due, null, "UTC", null, null)

    private fun dueTest(definition: ParameterDefinition) = DueTest(definition, today, DueReason.RHYTHM)

    private val allOk = listOf(reading(ph, 7.0), reading(nitrite, 0.0), reading(nitrate, 10.0))

    private fun widget(
        readings: List<LatestReading> = allOk,
        tests: List<DueTest> = emptyList(),
        pending: List<TaskOccurrence> = emptyList(),
    ) = widgetAquarium(setup, readings, tests, pending, today)

    @Test
    fun `widgetText carries the Aquarium photoId`() = runTest {
        val day = java.time.LocalDate.of(2026, 11, 2)
        val withPhoto = widgetAquarium(setup.copy(aquarium = setup.aquarium.copy(photoId = "p1")), allOk, emptyList(), emptyList(), today)
        assertEquals("p1", widgetText(withPhoto, day).photoId)
        assertNull(widgetText(widget(), day).photoId)
    }

    @Test
    fun `an alert value comes before an overdue task`() {
        val result = widget(listOf(reading(ph, 7.0), reading(nitrite, 0.5), reading(nitrate, 10.0)), pending = listOf(occurrence("1", yesterday)))
        assertEquals(NextStep.Value(nitrite, ReadingLevel.ALERT), result.next)
        assertEquals(WidgetLight.ALERT, result.light)
    }

    @Test
    fun `an overdue task comes before due tests`() {
        val result = widget(tests = listOf(dueTest(ph)), pending = listOf(occurrence("1", yesterday)))
        val next = assertIs<NextStep.Task>(result.next)
        assertTrue(next.isOverdue)
        assertEquals(WidgetLight.OK, result.light)
    }

    @Test
    fun `due tests come before an attention value`() {
        val result = widget(listOf(reading(ph, 7.8), reading(nitrite, 0.0), reading(nitrate, 10.0)), tests = listOf(dueTest(nitrate)))
        assertEquals(NextStep.Tests(listOf(dueTest(nitrate))), result.next)
        assertEquals(WidgetLight.ATTENTION, result.light)
    }

    @Test
    fun `an attention value comes before a task due today`() {
        val result = widget(listOf(reading(ph, 7.8), reading(nitrite, 0.0), reading(nitrate, 10.0)), pending = listOf(occurrence("1", today)))
        assertEquals(NextStep.Value(ph, ReadingLevel.ATTENTION), result.next)
    }

    @Test
    fun `a task due today shows as not overdue`() {
        val next = assertIs<NextStep.Task>(widget(pending = listOf(occurrence("1", today))).next)
        assertEquals(false, next.isOverdue)
    }

    @Test
    fun `nothing to do`() {
        val result = widget(pending = listOf(occurrence("1", tomorrow)))
        assertEquals(NextStep.Nothing, result.next)
        assertEquals(WidgetLight.OK, result.light)
    }

    @Test
    fun `a task of another aquarium is ignored`() {
        val result = widget(pending = listOf(occurrence("1", yesterday, aquariumId = "a2")))
        assertEquals(NextStep.Nothing, result.next)
        assertTrue(result.dueTasks.isEmpty())
    }

    @Test
    fun `due tasks are today's and overdue ones, at most 3`() {
        val pending = listOf(occurrence("1", yesterday), occurrence("2", today), occurrence("3", today), occurrence("4", today), occurrence("5", tomorrow))
        assertEquals(listOf("1", "2", "3"), widget(pending = pending).dueTasks.map { it.id })
    }

    private class Fixture {
        private val path = Files.createTempDirectory("aqualog-widget").resolve("aqualog.db").toString()
        private val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        private val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val repository = RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L })

        suspend fun createAquarium(name: String) = repository.createConfiguredAquarium(
            name = name,
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            access = AquariumCreationAccess.PRO,
        ).aquarium

        fun close() = database.close()
    }

    @Test
    fun `a missing pinned aquarium falls back to the current one`() = runTest {
        val fixture = Fixture()
        val pinned = fixture.createAquarium("Pinned")
        val current = fixture.createAquarium("Current")
        fixture.repository.selectAquarium(current.id)
        fixture.repository.archiveAquarium(pinned.id)
        assertEquals(current.id, fixture.repository.loadWidgetAquarium(pinned.id, today, "UTC")!!.aquarium.id)
        assertEquals(listOf(current.id), fixture.repository.loadWidgetAquariums(today, "UTC").map { it.aquarium.id })
        fixture.close()
    }

    @Test
    fun `a pinned aquarium wins over the current one`() = runTest {
        val fixture = Fixture()
        val pinned = fixture.createAquarium("Pinned")
        val current = fixture.createAquarium("Current")
        fixture.repository.selectAquarium(current.id)
        assertEquals(pinned.id, fixture.repository.loadWidgetAquarium(pinned.id, today, "UTC")!!.aquarium.id)
        fixture.close()
    }

    @Test
    fun `no aquarium gives null`() = runTest {
        val fixture = Fixture()
        assertNull(fixture.repository.loadWidgetAquarium(null, today, "UTC"))
        assertEquals(emptyList(), fixture.repository.loadWidgetAquariums(today, "UTC"))
        fixture.close()
    }

    private val encyclopedia = decodeSpecies(File("src/commonMain/composeResources/files/encyclopedia/species.json").readText())

    private fun inhabitant(id: String, speciesId: String?, family: DrawingFamily, count: Int, customName: String? = null) =
        Inhabitant(id, "a1", speciesId, customName, family, count, 0L)

    @Test
    fun `the widget shows the 3 largest swimming groups, plants excluded, ties by key`() {
        val neon = encyclopedia.first { it.family == DrawingFamily.TETRA }
        val groups = widgetGroups(
            listOf(
                inhabitant("1", neon.id, DrawingFamily.TETRA, 6),
                inhabitant("2", null, DrawingFamily.PLANT, 30),
                inhabitant("3", null, DrawingFamily.CICHLID, 2, "B"),
                inhabitant("4", null, DrawingFamily.CATFISH, 2, "A"),
                inhabitant("5", null, DrawingFamily.SNAIL, 1),
                inhabitant("6", neon.id, DrawingFamily.TETRA, 4),
            ),
            encyclopedia,
        )
        assertEquals(listOf(DrawingFamily.TETRA, DrawingFamily.CATFISH, DrawingFamily.CICHLID), groups.map { it.family })
        assertEquals(listOf(10, 2, 2), groups.map { it.count })
        assertEquals(neon, groups.first().species)
        assertNull(groups[1].species)
    }

    @Test
    fun `without the encyclopedia the groups keep the stored family`() {
        val groups = widgetGroups(listOf(inhabitant("1", "neon", DrawingFamily.TETRA, 3)), null)
        assertEquals(listOf(DrawingFamily.TETRA), groups.map { it.family })
        assertNull(groups.single().species)
    }
}
