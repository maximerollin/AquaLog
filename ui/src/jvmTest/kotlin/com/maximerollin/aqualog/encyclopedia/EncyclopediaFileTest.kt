package com.maximerollin.aqualog.encyclopedia

import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.decodeSpecies
import com.maximerollin.aqualog.shared.encyclopedia.speciesProblems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(s.names.getValue("fr"), s.localName("xx"))
        assertEquals(s.names.getValue("en"), s.localName("en"))
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
}
