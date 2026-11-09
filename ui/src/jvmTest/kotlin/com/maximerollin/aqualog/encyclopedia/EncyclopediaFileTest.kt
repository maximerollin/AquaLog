package com.maximerollin.aqualog.encyclopedia

import com.maximerollin.aqualog.habitants.searchSpecies
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.encyclopedia.CriterionKind
import com.maximerollin.aqualog.shared.encyclopedia.CriterionStatus
import com.maximerollin.aqualog.shared.encyclopedia.Reason
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.shared.encyclopedia.speciesProblems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncyclopediaFileTest {
    private val resources = File("src/commonMain/composeResources")
    private val species = decodeSpecies(File(resources, "files/encyclopedia/species.json").readText())

    @Test fun shippedFileIsValid() {
        assertTrue(species.isNotEmpty())
        assertEquals(emptyList(), speciesProblems(species))
    }

    @Test fun everyDrawingExists() {
        for (s in species) {
            val name = s.drawing ?: continue
            assertTrue(
                File(resources, "drawable").listFiles().orEmpty().any { it.nameWithoutExtension == name },
                "${s.id}: missing drawable $name",
            )
        }
    }

    @Test fun localNameFallsBack() {
        val s = species.first()
        assertEquals(s.names.getValue("en"), s.localName("xx"))
        assertEquals(s.names.getValue("fr"), s.copy(names = s.names - "en").localName("xx"))
        assertEquals(s.names.getValue("en"), s.localName("en"))
        assertEquals(s.names.getValue("fr"), s.localName("fr-FR"))
        assertEquals(s.names.getValue("de"), s.localName("DE-at"))
        assertEquals(s.scientificName, s.copy(names = emptyMap()).localName("en"))
    }

    @Test fun inhabitantNameFallsBackWithoutCrashing() {
        val s = species.first()
        fun inhabitant(speciesId: String?, customName: String?) =
            Inhabitant("i", "a", speciesId, customName, s.family, 1, 0L)
        assertEquals(s.names.getValue("en"), inhabitant(s.id, null).displayName(species, "en"))
        assertEquals("Mon poisson", inhabitant(null, "Mon poisson").displayName(species, "en"))
        assertEquals(s.id, inhabitant(s.id, null).displayName(null, "en"))
        assertEquals("gone", inhabitant("gone", null).displayName(species, "en"))
    }

    @Test fun poissonRougeIsTooColdForATropicalTank() {
        val goldfish = species.single { it.id == "poisson-rouge" }
        val temperature = ParameterDefinition(
            "d-temperature", "a", BuiltInParameter.TEMPERATURE, true, 0,
            OnboardingPresets.presetUnit(BuiltInParameter.TEMPERATURE), 1, null, null,
        )
        val setup = AquariumSetup(Aquarium("a", "Tropical", 200.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED), listOf(temperature))
        val readings = listOf(LatestReading("d-temperature", 28.0, 0L))
        val check = assessSpecies(goldfish, setup, readings, emptyList(), goldfish.minGroup)
            .checks.single { it.kind == CriterionKind.TEMPERATURE }
        assertEquals(CriterionStatus.PROBLEM, check.status)
        assertEquals(Reason.TOO_WARM, check.reason)
    }

    @Test fun aPlantGetsNoFishVerdict() {
        val anubias = species.single { it.id == "anubias" }
        val setup = AquariumSetup(Aquarium("a", "Neuf", 20.0, VolumeUnit.LITERS, 0L, AquariumProfile.CYCLING), emptyList())
        val assessment = assessSpecies(anubias, setup, emptyList(), emptyList(), anubias.minGroup)
        assertFalse(assessment.cyclingWarning)
        assertTrue(assessment.checks.none { it.kind == CriterionKind.VOLUME || it.kind == CriterionKind.GROUP })
    }

    @Test fun searchFindsTheShippedSpecies() {
        fun ids(query: String) = searchSpecies(species, query, "fr", null).map { it.id }
        assertTrue("neon" in ids("neon") && "neon-noir" in ids("neon"))
        assertTrue("poisson-rouge" in ids("Carassius"))
        assertTrue("fougere-de-java" in ids("fougère"))
        assertTrue("fougere-de-java" in ids("fougere"))
    }
}
