package com.maximerollin.aqualog.shared.encyclopedia

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.usesPresetUnit

/** UNIT_NOT_COMPARED: the Aquarium has a reading, but in a unit the species ranges are not written in. */
enum class CriterionStatus { OK, CAUTION, PROBLEM, NOT_MEASURED, UNIT_NOT_COMPARED }

/** Declaration order is the badge priority. */
enum class CriterionKind { VOLUME, PH, TEMPERATURE, GH, KH, COHABITATION, GROUP }

enum class Reason {
    TANK_TOO_SMALL, WATER_TOO_HARD, WATER_TOO_SOFT, PH_TOO_HIGH, PH_TOO_LOW, TOO_WARM, TOO_COLD,
    EATS_SMALL_FISH, EATS_SHRIMP, FIN_NIPPING, TERRITORIAL, EATS_PLANTS, GROUP_TOO_SMALL,
}

data class CriterionCheck(
    val kind: CriterionKind,
    val status: CriterionStatus,
    val reason: Reason? = null,
    val aquariumValue: Double? = null,
    val otherSpeciesId: String? = null,
)

enum class Fit { GOOD, CAUTION, BAD }

data class Assessment(
    val species: Species,
    val fit: Fit,
    val checks: List<CriterionCheck>,
    val problems: Int,
    val badgeReason: Reason?,
    val cyclingWarning: Boolean,
)

/** A species group already living in the Aquarium; [species] is null for a custom one. */
data class PresentGroup(val species: Species?, val family: DrawingFamily, val count: Int)

private const val LITERS_PER_US_GALLON = 3.785411784
private const val TOLERANCE = 0.1
private const val PREY_MAX_SIZE_CM = 4.0

fun assessSpecies(
    species: Species,
    setup: AquariumSetup,
    readings: List<LatestReading>,
    present: List<PresentGroup>,
    count: Int = species.minGroup,
): Assessment {
    val checks = mutableListOf<CriterionCheck>()
    val isPlant = species.kind == SpeciesKind.PLANT

    val liters = setup.aquarium.volume * if (setup.aquarium.volumeUnit == VolumeUnit.US_GALLONS) LITERS_PER_US_GALLON else 1.0
    val minVolume = species.minVolumeLiters
    if (!isPlant && minVolume != null) {
        checks += if (liters < minVolume) CriterionCheck(CriterionKind.VOLUME, CriterionStatus.PROBLEM, Reason.TANK_TOO_SMALL, liters)
        else CriterionCheck(CriterionKind.VOLUME, CriterionStatus.OK, aquariumValue = liters)
    }

    fun water(kind: CriterionKind, parameter: BuiltInParameter, range: ValueRange?, low: Reason, high: Reason) {
        if (range == null) return
        // Species ranges are in the preset units (°C, °dGH, °dKH): a value in °F or another kit's scale is not judged.
        val definition = setup.parameters.firstOrNull { it.parameter == parameter && it.isActive }
        val value = readings.firstOrNull { it.parameterDefinitionId == definition?.id }?.value
        if (value == null || definition?.usesPresetUnit() != true) {
            checks += CriterionCheck(kind, if (value == null) CriterionStatus.NOT_MEASURED else CriterionStatus.UNIT_NOT_COMPARED)
            return
        }
        val gap = if (value < range.min) range.min - value else if (value > range.max) value - range.max else 0.0
        val status = when {
            gap == 0.0 -> CriterionStatus.OK
            gap < TOLERANCE * (range.max - range.min) -> CriterionStatus.CAUTION
            else -> CriterionStatus.PROBLEM
        }
        checks += CriterionCheck(kind, status, if (status == CriterionStatus.OK) null else if (value < range.min) low else high, value)
    }
    water(CriterionKind.PH, BuiltInParameter.PH, species.ph, Reason.PH_TOO_LOW, Reason.PH_TOO_HIGH)
    water(CriterionKind.TEMPERATURE, BuiltInParameter.TEMPERATURE, species.temperatureC, Reason.TOO_COLD, Reason.TOO_WARM)
    water(CriterionKind.GH, BuiltInParameter.GH, species.gh, Reason.WATER_TOO_SOFT, Reason.WATER_TOO_HARD)
    water(CriterionKind.KH, BuiltInParameter.KH, species.kh, Reason.WATER_TOO_SOFT, Reason.WATER_TOO_HARD)

    val cohabitation = present.mapNotNull { it.species }.flatMap { conflicts(species, it, it.id) + conflicts(it, species, it.id) }.distinct()
    checks += cohabitation.ifEmpty { listOf(CriterionCheck(CriterionKind.COHABITATION, CriterionStatus.OK)) }

    if (!isPlant) {
        checks += if (count < species.minGroup) CriterionCheck(CriterionKind.GROUP, CriterionStatus.CAUTION, Reason.GROUP_TOO_SMALL, count.toDouble())
        else CriterionCheck(CriterionKind.GROUP, CriterionStatus.OK, aquariumValue = count.toDouble())
    }

    val problems = checks.count { it.status == CriterionStatus.PROBLEM }
    val ordered = checks.sortedBy { it.kind }
    val badge = ordered.firstOrNull { it.status == CriterionStatus.PROBLEM } ?: ordered.firstOrNull { it.status == CriterionStatus.CAUTION }
    return Assessment(
        species = species,
        fit = when {
            problems > 0 -> Fit.BAD
            checks.any { it.status == CriterionStatus.CAUTION } -> Fit.CAUTION
            else -> Fit.GOOD
        },
        checks = checks,
        problems = problems,
        badgeReason = badge?.reason,
        cyclingWarning = setup.aquarium.profile == AquariumProfile.CYCLING && species.kind == SpeciesKind.FISH,
    )
}

/** What [actor] does to [victim] when they share a tank; [presentId] is whichever of the two already lives there. */
private fun conflicts(actor: Species, victim: Species, presentId: String): List<CriterionCheck> {
    fun check(status: CriterionStatus, reason: Reason) =
        CriterionCheck(CriterionKind.COHABITATION, status, reason, otherSpeciesId = presentId)
    return buildList {
        val tags = actor.tags
        if (SpeciesTag.EATS_SMALL_FISH in tags && victim.kind == SpeciesKind.FISH && victim.adultSizeCm <= PREY_MAX_SIZE_CM) add(check(CriterionStatus.PROBLEM, Reason.EATS_SMALL_FISH))
        if (SpeciesTag.EATS_SHRIMP in tags && victim.kind == SpeciesKind.SHRIMP) add(check(CriterionStatus.PROBLEM, Reason.EATS_SHRIMP))
        if (SpeciesTag.FIN_NIPPER in tags && SpeciesTag.LONG_FINS in victim.tags) add(check(CriterionStatus.PROBLEM, Reason.FIN_NIPPING))
        if (SpeciesTag.TERRITORIAL in tags && actor.id == victim.id) add(check(CriterionStatus.CAUTION, Reason.TERRITORIAL))
        if (SpeciesTag.EATS_PLANTS in tags && victim.kind == SpeciesKind.PLANT) add(check(CriterionStatus.CAUTION, Reason.EATS_PLANTS))
    }
}
