package com.maximerollin.aqualog.shared

import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InhabitantRepositoryTest {
    private class Fixture {
        var now = 1_000L
        val database = createAquaLogDatabase(
            createJvmDatabaseBuilder(Files.createTempDirectory("aqualog-inhabitants").resolve("a.db").toString()),
        )
        private val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val repository = RoomAquariumRepository(database, ids::next, { now })

        suspend fun aquarium(name: String = "Amazonien") = repository.createConfiguredAquarium(
            name, 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
            OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED), AquariumCreationAccess.PRO,
        ).aquarium

        suspend fun neons(aquariumId: String, count: Int) =
            repository.addInhabitant(aquariumId, "neon", null, DrawingFamily.TETRA, count)
    }

    @Test
    fun `adding a group shows it present`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val added = f.neons(aquarium.id, 10)

        assertEquals(listOf(added), f.repository.observeInhabitants(aquarium.id).first())
        assertEquals(10, added.count)
        assertEquals(null, added.removedAtEpochMillis)
        f.database.close()
    }

    @Test
    fun `removing part of a group splits it`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val group = f.neons(aquarium.id, 10)
        f.now = 5_000L

        f.repository.removeInhabitants(group.id, 2)

        val present = f.repository.observeInhabitants(aquarium.id).first().single()
        assertEquals(group.id, present.id)
        assertEquals(8, present.count)
        val history = f.repository.observeInhabitantHistory(aquarium.id).first()
        val removed = history.single { it.removedAtEpochMillis != null }
        assertEquals(2, removed.count)
        assertEquals(5_000L, removed.removedAtEpochMillis)
        assertEquals(group.addedAtEpochMillis, removed.addedAtEpochMillis)
        assertEquals(2, history.size)
        f.database.close()
    }

    @Test
    fun `removing a whole group marks the row removed`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val group = f.neons(aquarium.id, 3)
        f.now = 2_000L

        f.repository.removeInhabitants(group.id, 3)

        assertEquals(emptyList(), f.repository.observeInhabitants(aquarium.id).first())
        val row = f.repository.observeInhabitantHistory(aquarium.id).first().single()
        assertEquals(group.id, row.id)
        assertEquals(2_000L, row.removedAtEpochMillis)
        f.database.close()
    }

    @Test
    fun `removing twice then re-adding keeps totals`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val group = f.neons(aquarium.id, 10)
        f.now = 2_000L
        f.repository.removeInhabitants(group.id, 2)
        f.now = 3_000L
        f.repository.removeInhabitants(group.id, 3)
        f.now = 4_000L
        f.neons(aquarium.id, 4)

        assertEquals(9, f.repository.observeInhabitants(aquarium.id).first().sumOf { it.count })
        val changes = f.repository.observeInhabitantTimeline(TimelineFilter()).first()
        assertEquals(
            listOf(
                Triple(1_000L, false, 10),
                Triple(2_000L, true, 2),
                Triple(3_000L, true, 3),
                Triple(4_000L, false, 4),
            ),
            changes.sortedBy { it.atEpochMillis }.map { Triple(it.atEpochMillis, it.removed, it.inhabitant.count) },
        )
        f.database.close()
    }

    @Test
    fun `a split then removing the remainder gives one add line and two removal lines`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val group = f.neons(aquarium.id, 10)
        f.now = 2_000L
        f.repository.removeInhabitants(group.id, 4)
        f.now = 3_000L
        f.repository.removeInhabitants(group.id, 6)

        assertEquals(emptyList(), f.repository.observeInhabitants(aquarium.id).first())
        val changes = f.repository.observeInhabitantTimeline(TimelineFilter()).first()
        assertEquals(
            listOf(Triple(1_000L, false, 10), Triple(2_000L, true, 4), Triple(3_000L, true, 6)),
            changes.map { Triple(it.atEpochMillis, it.removed, it.inhabitant.count) },
        )
        f.database.close()
    }

    @Test
    fun `removing more than present is rejected and changes nothing`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()
        val group = f.neons(aquarium.id, 3)

        assertFailsWith<IllegalArgumentException> { f.repository.removeInhabitants(group.id, 4) }
        assertFailsWith<IllegalArgumentException> { f.repository.removeInhabitants(group.id, 0) }

        assertEquals(listOf(group), f.repository.observeInhabitantHistory(aquarium.id).first())
        f.database.close()
    }

    @Test
    fun `a custom species needs a name and a count of at least one`() = runTest {
        val f = Fixture()
        val aquarium = f.aquarium()

        assertFailsWith<IllegalArgumentException> {
            f.repository.addInhabitant(aquarium.id, null, "  ", DrawingFamily.CICHLID, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            f.repository.addInhabitant(aquarium.id, null, null, DrawingFamily.CICHLID, 1)
        }
        assertFailsWith<IllegalArgumentException> { f.neons(aquarium.id, 0) }
        val custom = f.repository.addInhabitant(aquarium.id, null, "Poisson maison", DrawingFamily.CICHLID, 1)

        assertEquals("Poisson maison", custom.customName)
        assertEquals(listOf(custom), f.repository.observeInhabitantHistory(aquarium.id).first())
        f.database.close()
    }

    @Test
    fun `the Chronologie follows the Aquarium and since filters`() = runTest {
        val f = Fixture()
        val first = f.aquarium("First")
        f.now = 2_000L
        val second = f.aquarium("Second")
        f.neons(first.id, 5)
        f.now = 9_000L
        f.neons(second.id, 2)

        assertEquals(2, f.repository.observeInhabitantTimeline(TimelineFilter()).first().size)
        assertEquals(
            listOf("Second"),
            f.repository.observeInhabitantTimeline(TimelineFilter(aquariumId = second.id)).first().map { it.aquarium.name },
        )
        assertEquals(
            listOf(second.id),
            f.repository.observeInhabitantTimeline(TimelineFilter(sinceEpochMillisInclusive = 5_000L)).first()
                .map { it.inhabitant.aquariumId },
        )
        assertTrue(f.repository.observeInhabitantHistory(null).first().size == 2)
        f.database.close()
    }
}
