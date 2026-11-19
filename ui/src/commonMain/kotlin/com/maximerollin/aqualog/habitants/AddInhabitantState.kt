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
        species.matches(wanted, kind, species.localName(languageTag).folded(), species.scientificName.folded())
    }
}

private fun Species.matches(wanted: String, kind: SpeciesKind?, foldedName: String, foldedScientific: String) =
    (kind == null || this.kind == kind) && (foldedName.contains(wanted) || foldedScientific.contains(wanted))

/** The non-empty sections GOOD, CAUTION, BAD in that order, each alphabetical by localized name. */
fun groupedForAquarium(assessments: List<Assessment>, languageTag: String): Map<Fit, List<Assessment>> =
    SpeciesSearchIndex(assessments, languageTag).grouped("", null)

/**
 * The search's list, built once per encyclopedia, Aquarium and language: every species assessed, its names folded once
 * (not at every comparison of the sort), in the order shown. A keystroke then only filters it, as [searchSpecies] would,
 * and hands back the same [Assessment] objects each time, so the rows that stay on screen are not composed again.
 */
class SpeciesSearchIndex(assessments: List<Assessment>, languageTag: String) {
    private class Entry(val assessment: Assessment, val name: String, val scientific: String)

    private val entries = assessments
        .map { Entry(it, it.species.localName(languageTag).folded(), it.species.scientificName.folded()) }
        .sortedBy { it.name }

    /** The non-empty sections GOOD, CAUTION, BAD of the species of [kind] (all when null) whose names contain [query]. */
    fun grouped(query: String, kind: SpeciesKind?): Map<Fit, List<Assessment>> {
        val wanted = query.trim().folded()
        val found = entries.filter { it.assessment.species.matches(wanted, kind, it.name, it.scientific) }
        return Fit.entries.associateWith { fit -> found.filter { it.assessment.fit == fit }.map { it.assessment } }
            .filterValues { it.isNotEmpty() }
    }
}

/** The reasons the fiche's sentence names: the first two, problems before precautions. */
fun explanationReasons(assessment: Assessment): List<Reason> =
    (assessment.checks.filter { it.status == CriterionStatus.PROBLEM } + assessment.checks.filter { it.status == CriterionStatus.CAUTION })
        .mapNotNull { it.reason }.distinct().take(2)

/** Upper-cases the first letter, unless the fragment opens with a symbol that keeps its case (« pH »). */
fun sentenceStart(fragment: String): String =
    if (fragment.length > 1 && fragment[1].isUpperCase()) fragment else fragment.replaceFirstChar(Char::uppercaseChar)
