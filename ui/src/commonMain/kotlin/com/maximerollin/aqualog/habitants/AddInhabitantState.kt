package com.maximerollin.aqualog.habitants

import com.maximerollin.aqualog.encyclopedia.localName
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.CriterionStatus
import com.maximerollin.aqualog.shared.encyclopedia.Reason
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import java.text.Normalizer

/**
 * « Ajouter un habitant » for one Aquarium: the search, then a species fiche ([ficheSpeciesId]) or the custom form ([custom]).
 * [species] is null while the encyclopedia loads, empty when it could not be read ([loadFailed]).
 */
data class AddInhabitantUiState(
    val aquariumId: String,
    val species: List<Species>? = null,
    val loadFailed: Boolean = false,
    val query: String = "",
    val kind: SpeciesKind? = null,
    val ficheSpeciesId: String? = null,
    val custom: Boolean = false,
    val count: Int = 1,
    val customName: String = "",
    val customFamily: DrawingFamily? = null,
    val showCustomError: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val ficheSpecies: Species? get() = ficheSpeciesId?.let { id -> species?.firstOrNull { it.id == id } }
}

private val marks = Regex("\\p{M}+")

private fun String.folded() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(marks, "").lowercase()

/** Species of [kind] (all when null) whose localized or scientific name contains [query], ignoring case and accents. */
fun searchSpecies(all: List<Species>, query: String, languageTag: String, kind: SpeciesKind?): List<Species> {
    val wanted = query.trim().folded()
    return all.filter { species ->
        (kind == null || species.kind == kind) &&
            (species.localName(languageTag).folded().contains(wanted) || species.scientificName.folded().contains(wanted))
    }
}

/** The non-empty sections GOOD, CAUTION, BAD in that order, each alphabetical by localized name. */
fun groupedForAquarium(assessments: List<Assessment>, languageTag: String): Map<Fit, List<Assessment>> =
    Fit.entries.associateWith { fit ->
        assessments.filter { it.fit == fit }.sortedBy { it.species.localName(languageTag).folded() }
    }.filterValues { it.isNotEmpty() }

/** The reasons the fiche's sentence names: the first two, problems before precautions. */
fun explanationReasons(assessment: Assessment): List<Reason> =
    (assessment.checks.filter { it.status == CriterionStatus.PROBLEM } + assessment.checks.filter { it.status == CriterionStatus.CAUTION })
        .mapNotNull { it.reason }.distinct().take(2)

/** Upper-cases the first letter, unless the fragment opens with a symbol that keeps its case (« pH »). */
fun sentenceStart(fragment: String): String =
    if (fragment.length > 1 && fragment[1].isUpperCase()) fragment else fragment.replaceFirstChar(Char::uppercaseChar)
