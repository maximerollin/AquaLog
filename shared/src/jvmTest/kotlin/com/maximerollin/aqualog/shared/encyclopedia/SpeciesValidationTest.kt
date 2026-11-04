package com.maximerollin.aqualog.shared.encyclopedia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

class SpeciesValidationTest {
    private fun entry(
        id: String = "neon",
        wikidata: String = "Q131431",
        family: String = "TETRA",
        names: String = """{"fr":"Néon","en":"Neon","de":"Neon","it":"Neon","es":"Neon"}""",
        ph: String = """{"min":5.0,"max":7.0}""",
        minVolume: String = "54",
        minGroup: Int = 6,
        scientificName: String = "Paracheirodon innesi",
        adultSize: Double = 3.5,
        lifespan: Int = 4,
    ) = """{"id":"$id","wikidata":"$wikidata","scientificName":"$scientificName","names":$names,
        "kind":"FISH","family":"$family","motion":"SCHOOL","ph":$ph,"temperatureC":null,"gh":null,"kh":null,
        "minVolumeLiters":$minVolume,"adultSizeCm":$adultSize,"minGroup":$minGroup,"tags":["PEACEFUL"],"diet":"OMNIVORE",
        "lifespanYears":$lifespan,"zone":"MIDDLE","color":4278190335,"drawing":null}"""

    private fun problems(vararg entries: String) = speciesProblems(decodeSpecies("[${entries.joinToString(",")}]"))

    private fun assertOneProblemNaming(id: String, problems: List<String>) {
        assertEquals(1, problems.size, problems.toString())
        assertTrue(id in problems.single(), problems.single())
    }

    @Test fun validEntryHasNoProblems() = assertEquals(emptyList(), problems(entry()))

    @Test fun missingLanguageKey() =
        assertOneProblemNaming("neon", problems(entry(names = """{"fr":"Néon","en":"Neon","de":"Neon","it":"Neon"}""")))

    @Test fun minAboveMax() =
        assertOneProblemNaming("neon", problems(entry(ph = """{"min":8.0,"max":7.0}""")))

    @Test fun unknownFamilyFailsDecoding() {
        assertFailsWith<SerializationException> { decodeSpecies("[${entry(family = "WHALE")}]") }
    }

    @Test fun duplicateId() = assertOneProblemNaming("neon", problems(entry(), entry(wikidata = "Q1")))

    @Test fun duplicateWikidata() = assertOneProblemNaming("neon-2", problems(entry(), entry(id = "neon-2")))

    @Test fun idNotKebabCase() {
        for (id in listOf("Neon", "neon_tetra", "neon--tetra", "-neon", "néon")) assertOneProblemNaming(id, problems(entry(id = id)))
    }

    @Test fun kebabCaseIdWithDigitsIsValid() = assertEquals(emptyList(), problems(entry(id = "neon-2")))

    @Test fun unknownLanguageKey() = assertOneProblemNaming(
        "neon",
        problems(entry(names = """{"fr":"Néon","en":"Neon","de":"Neon","it":"Neon","es":"Neon","pt":"Néon"}""")),
    )

    @Test fun blankScientificName() = assertOneProblemNaming("neon", problems(entry(scientificName = " ")))

    @Test fun nonPositiveAdultSize() = assertOneProblemNaming("neon", problems(entry(adultSize = 0.0)))

    @Test fun nonPositiveLifespan() = assertOneProblemNaming("neon", problems(entry(lifespan = 0)))

    @Test fun negativeRange() = assertOneProblemNaming("neon", problems(entry(ph = """{"min":-1.0,"max":7.0}""")))

    @Test fun minGroupBelowOne() = assertOneProblemNaming("neon", problems(entry(minGroup = 0)))

    @Test fun badWikidata() = assertOneProblemNaming("neon", problems(entry(wikidata = "131431")))

    @Test fun fishWithoutMinVolume() = assertOneProblemNaming("neon", problems(entry(minVolume = "null")))
}
