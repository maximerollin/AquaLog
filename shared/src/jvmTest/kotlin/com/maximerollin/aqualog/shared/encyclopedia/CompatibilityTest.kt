package com.maximerollin.aqualog.shared.encyclopedia

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityTest {
    private val definitions = listOf(
        BuiltInParameter.PH, BuiltInParameter.TEMPERATURE, BuiltInParameter.GH, BuiltInParameter.KH,
    ).map { ParameterDefinition("d-${it.storageValue}", "a", it, true, 0, OnboardingPresets.presetUnit(it), 1, null, null) }

    private fun setup(
        volume: Double = 120.0,
        unit: VolumeUnit = VolumeUnit.LITERS,
        profile: AquariumProfile = AquariumProfile.ESTABLISHED,
        defs: List<ParameterDefinition> = definitions,
    ) = AquariumSetup(Aquarium("a", "Bac", volume, unit, 0L, profile), defs)

    private fun reading(p: BuiltInParameter, v: Double) = LatestReading("d-${p.storageValue}", v, 0L)

    private fun readings(ph: Double = 7.0, t: Double = 25.0, gh: Double = 8.0, kh: Double = 4.0) = listOf(
        reading(BuiltInParameter.PH, ph), reading(BuiltInParameter.TEMPERATURE, t),
        reading(BuiltInParameter.GH, gh), reading(BuiltInParameter.KH, kh),
    )

    private fun species(
        id: String,
        kind: SpeciesKind = SpeciesKind.FISH,
        tags: Set<SpeciesTag> = setOf(SpeciesTag.PEACEFUL),
        size: Double = 3.0,
        minVolume: Int? = 54,
        minGroup: Int = 6,
        gh: ValueRange? = ValueRange(4.0, 12.0),
    ) = Species(
        id, "Q1", id, mapOf("fr" to id), kind,
        when (kind) {
            SpeciesKind.FISH -> DrawingFamily.TETRA
            SpeciesKind.SHRIMP -> DrawingFamily.SHRIMP
            SpeciesKind.SNAIL -> DrawingFamily.SNAIL
            SpeciesKind.PLANT -> DrawingFamily.PLANT
        },
        MotionFamily.SCHOOL, ValueRange(5.0, 7.5), ValueRange(22.0, 27.0), gh, ValueRange(1.0, 8.0),
        minVolume, size, minGroup, tags, Diet.OMNIVORE, 3, SwimZone.MIDDLE, 0xFF000000, null,
    )

    private val neon = species("neon")
    private val scalaire = species("scalaire", tags = setOf(SpeciesTag.EATS_SMALL_FISH), size = 15.0, minVolume = 200, minGroup = 1)

    private fun assess(s: Species, setup: AquariumSetup = setup(), r: List<LatestReading> = readings(), present: List<PresentGroup> = emptyList(), count: Int = s.minGroup) =
        assessSpecies(s, setup, r, present, count)

    private fun Assessment.check(kind: CriterionKind) = checks.single { it.kind == kind }

    @Test
    fun `a small peaceful fish in a matching tank is GOOD`() {
        val a = assess(neon)
        assertEquals(Fit.GOOD, a.fit)
        listOf(CriterionKind.PH, CriterionKind.TEMPERATURE, CriterionKind.GH).forEach { assertEquals(CriterionStatus.OK, a.check(it).status) }
        assertNull(a.badgeReason)
        assertEquals(0, a.problems)
    }

    @Test
    fun `the mockup's scalaire is BAD with 2 problems`() {
        val a = assess(scalaire, present = listOf(PresentGroup(neon, neon.family, 10)))
        assertEquals(Fit.BAD, a.fit)
        assertEquals(CriterionStatus.PROBLEM, a.check(CriterionKind.VOLUME).status)
        assertEquals(Reason.TANK_TOO_SMALL, a.check(CriterionKind.VOLUME).reason)
        val c = a.check(CriterionKind.COHABITATION)
        assertEquals(CriterionStatus.PROBLEM, c.status)
        assertEquals(Reason.EATS_SMALL_FISH, c.reason)
        assertEquals("neon", c.otherSpeciesId)
        assertEquals(2, a.problems)
        assertEquals(Reason.TANK_TOO_SMALL, a.badgeReason)
    }

    @Test
    fun `a predator already present makes the small new species BAD`() {
        val a = assess(neon, present = listOf(PresentGroup(scalaire, scalaire.family, 1)))
        assertEquals(Fit.BAD, a.fit)
        assertEquals("scalaire", a.check(CriterionKind.COHABITATION).otherSpeciesId)
    }

    @Test
    fun `just outside a range is CAUTION, far outside is PROBLEM`() {
        val guppy = species("guppy", gh = ValueRange(6.0, 20.0))
        val near = assess(guppy, r = readings(gh = 5.0)).check(CriterionKind.GH)
        assertEquals(CriterionStatus.CAUTION, near.status)
        assertEquals(Reason.WATER_TOO_SOFT, near.reason)
        assertEquals(5.0, near.aquariumValue)
        assertEquals(CriterionStatus.PROBLEM, assess(guppy, r = readings(gh = 3.0)).check(CriterionKind.GH).status)
        assertEquals(Reason.WATER_TOO_HARD, assess(guppy, r = readings(gh = 30.0)).check(CriterionKind.GH).reason)
    }

    @Test
    fun `no reading gives NOT_MEASURED and no water verdict`() {
        val a = assess(neon, r = emptyList())
        listOf(CriterionKind.PH, CriterionKind.TEMPERATURE, CriterionKind.GH, CriterionKind.KH).forEach {
            assertEquals(CriterionStatus.NOT_MEASURED, a.check(it).status)
        }
        assertEquals(Fit.GOOD, a.fit)
        val tooSmall = assess(scalaire, r = emptyList())
        assertEquals(Fit.BAD, tooSmall.fit)
    }

    @Test
    fun `a water criterion read in a non-preset unit is UNIT_NOT_COMPARED, unread it is NOT_MEASURED`() {
        val fahrenheit = definitions.map { if (it.parameter == BuiltInParameter.TEMPERATURE) it.copy(unit = "°F") else it }
        val result = assess(neon, setup(defs = fahrenheit), readings(t = 77.0))
        val temperature = result.check(CriterionKind.TEMPERATURE)
        assertEquals(CriterionStatus.UNIT_NOT_COMPARED, temperature.status)
        assertNull(temperature.reason)
        assertNull(temperature.aquariumValue)
        assertEquals(Fit.GOOD, result.fit)
        assertEquals(CriterionStatus.NOT_MEASURED, assess(neon, setup(defs = fahrenheit), emptyList()).check(CriterionKind.TEMPERATURE).status)
    }

    @Test
    fun `US gallons are converted`() {
        val s = neon.copy(minVolumeLiters = 100)
        assertEquals(CriterionStatus.OK, assess(s, setup(30.0, VolumeUnit.US_GALLONS)).check(CriterionKind.VOLUME).status)
        assertEquals(CriterionStatus.PROBLEM, assess(s, setup(20.0, VolumeUnit.US_GALLONS)).check(CriterionKind.VOLUME).status)
    }

    @Test
    fun `a group below the minimum is CAUTION`() {
        val a = assess(neon, count = neon.minGroup - 1)
        assertEquals(CriterionStatus.CAUTION, a.check(CriterionKind.GROUP).status)
        assertEquals(Reason.GROUP_TOO_SMALL, a.check(CriterionKind.GROUP).reason)
        assertEquals(Fit.CAUTION, a.fit)
        assertEquals(Reason.GROUP_TOO_SMALL, a.badgeReason)
    }

    @Test
    fun `cycling warns for fish only`() {
        val cycling = setup(profile = AquariumProfile.CYCLING)
        val cherry = species("red-cherry", kind = SpeciesKind.SHRIMP, minVolume = null)
        assertTrue(assess(neon, cycling).cyclingWarning)
        assertFalse(assess(cherry, cycling).cyclingWarning)
        assertFalse(assess(neon).cyclingWarning)
    }

    @Test
    fun `territorial with the same species present is CAUTION`() {
        val t = species("oscar", tags = setOf(SpeciesTag.TERRITORIAL), size = 30.0, minVolume = 50, minGroup = 1)
        val a = assess(t, present = listOf(PresentGroup(t, t.family, 1)))
        assertEquals(CriterionStatus.CAUTION, a.check(CriterionKind.COHABITATION).status)
        assertEquals(Reason.TERRITORIAL, a.check(CriterionKind.COHABITATION).reason)
        assertEquals(Fit.CAUTION, a.fit)
    }

    @Test
    fun `plants skip volume and group`() {
        val p = species("java-fern", kind = SpeciesKind.PLANT, minVolume = 500, minGroup = 5)
        val a = assess(p, count = 1)
        assertTrue(a.checks.none { it.kind == CriterionKind.VOLUME || it.kind == CriterionKind.GROUP })
        assertEquals(Fit.GOOD, a.fit)
    }

    @Test
    fun `eats plants, shrimp and fin nipping are detected`() {
        val plant = species("fern", kind = SpeciesKind.PLANT)
        val shrimp = species("cherry", kind = SpeciesKind.SHRIMP)
        val betta = species("betta", tags = setOf(SpeciesTag.LONG_FINS))
        val goldfish = species("goldfish", tags = setOf(SpeciesTag.EATS_PLANTS), size = 20.0)
        val barb = species("barb", tags = setOf(SpeciesTag.FIN_NIPPER))
        val shrimper = species("shrimper", tags = setOf(SpeciesTag.EATS_SHRIMP), size = 8.0)
        assertEquals(Reason.EATS_PLANTS, assess(goldfish, present = listOf(PresentGroup(plant, plant.family, 1))).check(CriterionKind.COHABITATION).reason)
        assertEquals(Reason.EATS_PLANTS, assess(plant, present = listOf(PresentGroup(goldfish, goldfish.family, 1))).check(CriterionKind.COHABITATION).reason)
        assertEquals(Reason.EATS_SHRIMP, assess(shrimper, present = listOf(PresentGroup(shrimp, shrimp.family, 5))).check(CriterionKind.COHABITATION).reason)
        assertEquals(Reason.FIN_NIPPING, assess(barb, present = listOf(PresentGroup(betta, betta.family, 1))).check(CriterionKind.COHABITATION).reason)
        assertEquals(Reason.FIN_NIPPING, assess(betta, present = listOf(PresentGroup(barb, barb.family, 6))).check(CriterionKind.COHABITATION).reason)
        // custom species (no Species) are skipped
        assertEquals(CriterionStatus.OK, assess(neon, present = listOf(PresentGroup(null, DrawingFamily.TETRA, 3))).check(CriterionKind.COHABITATION).status)
    }

    @Test
    fun `eats snails is detected both ways`() {
        val snail = species("nerite", kind = SpeciesKind.SNAIL)
        val loach = species("loach", tags = setOf(SpeciesTag.EATS_SNAILS), size = 10.0)
        val forward = assess(loach, present = listOf(PresentGroup(snail, snail.family, 3))).check(CriterionKind.COHABITATION)
        assertEquals(Reason.EATS_SNAILS, forward.reason)
        assertEquals(Fit.BAD, assess(loach, present = listOf(PresentGroup(snail, snail.family, 3))).fit)
        val reverse = assess(snail, present = listOf(PresentGroup(loach, loach.family, 3)))
        assertEquals(Reason.EATS_SNAILS, reverse.check(CriterionKind.COHABITATION).reason)
        assertEquals(Fit.BAD, reverse.fit)
    }

    @Test
    fun `eats snails ignores shrimp`() {
        val shrimp = species("cherry", kind = SpeciesKind.SHRIMP)
        val loach = species("loach", tags = setOf(SpeciesTag.EATS_SNAILS), size = 10.0)
        assertEquals(CriterionStatus.OK, assess(loach, present = listOf(PresentGroup(shrimp, shrimp.family, 5))).check(CriterionKind.COHABITATION).status)
    }
}
