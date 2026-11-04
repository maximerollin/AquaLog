package com.maximerollin.aqualog.shared.encyclopedia

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ValueRange(val min: Double, val max: Double)

enum class SpeciesKind { FISH, SHRIMP, SNAIL, PLANT }

enum class DrawingFamily { TETRA, LIVEBEARER, CICHLID, LABYRINTH, CYPRINID, CATFISH, SHRIMP, SNAIL, PLANT }

enum class MotionFamily { SCHOOL, BOTTOM, GLIDE, CRAWL, SLOW }

enum class SpeciesTag { EATS_SMALL_FISH, EATS_SHRIMP, FIN_NIPPER, LONG_FINS, TERRITORIAL, EATS_PLANTS, PEACEFUL }

enum class Diet { OMNIVORE, HERBIVORE, CARNIVORE, ALGAE }

enum class SwimZone { BOTTOM, MIDDLE, SURFACE }

@Serializable
data class Species(
    val id: String,
    val wikidata: String,
    val scientificName: String,
    val names: Map<String, String>,
    val kind: SpeciesKind,
    val family: DrawingFamily,
    val motion: MotionFamily,
    val ph: ValueRange?,
    val temperatureC: ValueRange?,
    val gh: ValueRange?,
    val kh: ValueRange?,
    val minVolumeLiters: Int?,
    val adultSizeCm: Double,
    val minGroup: Int,
    val tags: Set<SpeciesTag>,
    val diet: Diet,
    val lifespanYears: Int,
    val zone: SwimZone,
    /** ARGB, used to recolour the silhouette. */
    val color: Long,
    /** Dedicated vector name, e.g. "species_neon". */
    val drawing: String?,
)

val SPECIES_LANGUAGES = listOf("fr", "en", "de", "it", "es")

private val wikidataId = Regex("^Q\\d+$")

// Strict on purpose: a typo in the shipped file (unknown key or enum name) must fail CI.
private val speciesJson = Json { ignoreUnknownKeys = false }

fun decodeSpecies(json: String): List<Species> = speciesJson.decodeFromString(json)

fun speciesProblems(species: List<Species>): List<String> {
    val problems = mutableListOf<String>()
    species.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "$it: duplicate id" }
    for (s in species) {
        val missing = SPECIES_LANGUAGES.filter { s.names[it].isNullOrBlank() }
        if (missing.isNotEmpty()) problems += "${s.id}: missing names $missing"
        if (!wikidataId.matches(s.wikidata)) problems += "${s.id}: invalid wikidata '${s.wikidata}'"
        mapOf("ph" to s.ph, "temperatureC" to s.temperatureC, "gh" to s.gh, "kh" to s.kh).forEach { (name, r) ->
            if (r != null && r.min > r.max) problems += "${s.id}: $name min > max"
        }
        if (s.minGroup < 1) problems += "${s.id}: minGroup < 1"
        if (s.kind == SpeciesKind.FISH && s.minVolumeLiters == null) problems += "${s.id}: fish without minVolumeLiters"
    }
    return problems
}
