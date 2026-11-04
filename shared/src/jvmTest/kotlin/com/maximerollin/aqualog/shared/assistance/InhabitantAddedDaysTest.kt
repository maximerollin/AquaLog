package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.encyclopedia.Diet
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.MotionFamily
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import com.maximerollin.aqualog.shared.encyclopedia.SwimZone
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class InhabitantAddedDaysTest {
    private val paris = "Europe/Paris"

    private fun species(id: String, kind: SpeciesKind) = Species(
        id, "Q1", id, mapOf("fr" to id), kind, DrawingFamily.TETRA, MotionFamily.SCHOOL, null, null, null, null,
        null, 3.0, 1, emptySet(), Diet.OMNIVORE, 3, SwimZone.MIDDLE, 0xFF000000, null,
    )

    private val encyclopedia = listOf(
        species("neon", SpeciesKind.FISH), species("crevette", SpeciesKind.SHRIMP),
        species("nerite", SpeciesKind.SNAIL), species("anubias", SpeciesKind.PLANT),
    )

    private fun inhabitant(
        speciesId: String?,
        family: DrawingFamily = DrawingFamily.TETRA,
        day: Int = 10,
        hour: Int = 12,
        removed: Boolean = false,
    ): Inhabitant {
        val at = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, ZoneId.of(paris)).toInstant().toEpochMilli()
        return Inhabitant("i-$speciesId-$day", "a", speciesId, if (speciesId == null) "Perso" else null, family, 3, at, if (removed) at + 1 else null)
    }

    @Test
    fun `fish, shrimp and snails trigger, plants do not`() {
        val days = inhabitantAddedDays(
            listOf(
                inhabitant("neon", day = 1), inhabitant("crevette", day = 2), inhabitant("nerite", day = 3),
                inhabitant("anubias", DrawingFamily.PLANT, day = 4),
            ),
            encyclopedia, paris,
        )
        assertEquals(listOf(TaskDate(2026, 11, 1), TaskDate(2026, 11, 2), TaskDate(2026, 11, 3)), days)
    }

    @Test
    fun `a custom species triggers unless it is a plant, an unknown id follows its family`() {
        val days = inhabitantAddedDays(
            listOf(
                inhabitant(null, DrawingFamily.CATFISH, day = 1), inhabitant(null, DrawingFamily.PLANT, day = 2),
                inhabitant("inconnu", DrawingFamily.TETRA, day = 3), inhabitant("inconnu2", DrawingFamily.PLANT, day = 4),
            ),
            encyclopedia, paris,
        )
        assertEquals(listOf(TaskDate(2026, 11, 1), TaskDate(2026, 11, 3)), days)
    }

    @Test
    fun `the day is the Aquarium's, a removed group still counts, a day appears once`() {
        // 00:00 Paris on the 11th is 23:00 UTC on the 10th.
        val late = inhabitant("neon", day = 10, hour = 23)
        val early = inhabitant("neon", day = 11, hour = 0).copy(id = "other")
        val removed = inhabitant("neon", day = 10, hour = 8, removed = true).copy(id = "third")
        assertEquals(listOf(TaskDate(2026, 11, 10), TaskDate(2026, 11, 11)), inhabitantAddedDays(listOf(late, early, removed), encyclopedia, paris))
        assertEquals(listOf(TaskDate(2026, 11, 10)), inhabitantAddedDays(listOf(late, early, removed), encyclopedia, "UTC"))
    }
}
