package com.maximerollin.aqualog.habitants

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.encyclopedia.displayName
import com.maximerollin.aqualog.encyclopedia.localName
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.CriterionCheck
import com.maximerollin.aqualog.shared.encyclopedia.CriterionKind
import com.maximerollin.aqualog.shared.encyclopedia.CriterionStatus
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.Reason
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.ValueRange
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Minus
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor
import com.maximerollin.aqualog.ui.theme.levelTextColor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One species judged against the Aquarium: verdict, criteria, facts, counter and the add button (never disabled by the verdict). */
@Composable
internal fun SpeciesFiche(
    species: Species,
    add: AddInhabitantUiState,
    setup: AquariumSetup,
    readings: List<LatestReading>,
    inhabitants: List<Inhabitant>,
    present: List<PresentGroup>,
    encyclopedia: List<Species>,
    language: String,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val assessment = remember(species, setup, readings, present, add.count) {
        assessSpecies(species, setup, readings, present, add.count)
    }
    var showDetails by rememberSaveable(species.id) { mutableStateOf(false) }
    val name = species.localName(language)
    val formatter = currentLocalizedFormatters()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                )
                Text(
                    species.scientificName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BackButton(viewModel::backInAddInhabitant)
        }
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(
                    when (assessment.fit) {
                        Fit.GOOD -> Res.string.habitants_verdict_good
                        Fit.CAUTION -> Res.string.habitants_verdict_caution
                        Fit.BAD -> Res.string.habitants_verdict_bad
                    },
                    setup.aquarium.name,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = fitColor(assessment.fit),
            )
            Text(explanation(assessment), style = MaterialTheme.typography.bodyMedium)
        }
        if (assessment.cyclingWarning) CyclingBanner()
        Column {
            CarnetSectionTitle(
                stringResource(Res.string.habitants_compared),
                actionLabel = stringResource(Res.string.habitants_details).takeIf { assessment.fit == Fit.GOOD && !showDetails },
                onAction = { showDetails = true },
            )
            if (assessment.fit != Fit.GOOD || showDetails) {
                // COHABITATION may hold several checks, one per conflicting present species: each gets its row.
                assessment.checks.forEach { check ->
                    CarnetDivider()
                    CheckRow(check, species, setup, inhabitants, encyclopedia, language, formatter)
                }
            }
        }
        if (assessment.checks.any { it.status == CriterionStatus.NOT_MEASURED }) {
            OutlinedButton(
                onClick = { viewModel.openRapidSession() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.habitants_measure)) }
        }
        Column {
            Text(stringResource(Res.string.habitants_fact_size, formatter.number(species.adultSizeCm, 1)))
            Text(pluralStringResource(Res.plurals.habitants_fact_lifespan, species.lifespanYears, species.lifespanYears))
            Text(stringResource(Res.string.habitants_fact_group, species.minGroup))
        }
        Column {
            CarnetSectionTitle(stringResource(Res.string.habitants_count_title))
            if (species.minGroup > 1) {
                Text(
                    stringResource(Res.string.habitants_count_minimum, species.minGroup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InhabitantCounter(add.count, viewModel::changeInhabitantCount)
        }
        Button(
            onClick = viewModel::confirmAddInhabitant,
            enabled = !add.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                if (assessment.fit == Fit.BAD) {
                    stringResource(Res.string.habitants_add_anyway)
                } else {
                    stringResource(Res.string.habitants_add_button, add.count, name)
                },
                textAlign = TextAlign.Center,
            )
        }
        if (add.saveFailed) Text(stringResource(Res.string.habitants_add_failed), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
    }
}

/** One criterion: status mark, criterion and the species' range, then the Aquarium's value; read aloud as one line. */
@Composable
private fun CheckRow(
    check: CriterionCheck,
    species: Species,
    setup: AquariumSetup,
    inhabitants: List<Inhabitant>,
    encyclopedia: List<Species>,
    language: String,
    formatter: LocalizedFormatters,
) {
    val label: String
    val detail: String?
    val value: String
    when (check.kind) {
        CriterionKind.VOLUME -> {
            label = stringResource(Res.string.habitants_criterion_volume)
            detail = species.minVolumeLiters?.let { stringResource(Res.string.habitants_minimum_volume, it) }
            value = formatter.volume(setup.aquarium.volume, setup.aquarium.volumeUnit).replace(" ", NBSP)
        }
        CriterionKind.PH, CriterionKind.TEMPERATURE, CriterionKind.GH, CriterionKind.KH -> {
            val (parameter, range) = when (check.kind) {
                CriterionKind.PH -> BuiltInParameter.PH to species.ph
                CriterionKind.TEMPERATURE -> BuiltInParameter.TEMPERATURE to species.temperatureC
                CriterionKind.GH -> BuiltInParameter.GH to species.gh
                else -> BuiltInParameter.KH to species.kh
            }
            val definition = setup.parameters.firstOrNull { it.parameter == parameter && it.isActive }
            // pH is its own unit: « 7,2 », not « 7,2 pH ». Species ranges are in the preset unit, the value in the Aquarium's.
            fun withUnit(text: String, unit: String?) =
                listOfNotNull(text, unit?.takeIf { parameter != BuiltInParameter.PH && it.isNotBlank() }).joinToString(NBSP)
            label = parameter.label()
            detail = range?.let { withUnit(rangeText(it, formatter), OnboardingPresets.presetUnit(parameter)) }
            value = check.aquariumValue?.let { withUnit(formatter.number(it, definition?.precision ?: 1), definition?.unit) }
                ?: stringResource(if (check.status == CriterionStatus.UNIT_NOT_COMPARED) Res.string.habitants_unit_not_compared else Res.string.habitants_not_measured)
        }
        CriterionKind.COHABITATION -> {
            label = stringResource(Res.string.habitants_criterion_cohabitation)
            detail = check.reason?.explanation()
            val other = check.otherSpeciesId
            value = if (other != null) {
                val group = inhabitants.filter { it.speciesId == other }
                stringResource(
                    Res.string.habitants_present_group,
                    group.firstOrNull()?.displayName(encyclopedia, language) ?: other,
                    group.sumOf { it.count },
                )
            } else {
                inhabitants.map { it.displayName(encyclopedia, language) }.distinct().joinToString(", ")
                    .ifEmpty { stringResource(Res.string.habitants_none_present) }
            }
        }
        CriterionKind.GROUP -> {
            label = stringResource(Res.string.habitants_criterion_group)
            detail = stringResource(Res.string.habitants_group_minimum, species.minGroup)
            value = check.aquariumValue?.toInt()?.toString().orEmpty()
        }
    }
    val (mark, color) = when (check.status) {
        CriterionStatus.OK -> "✓" to MaterialTheme.colorScheme.primary
        CriterionStatus.CAUTION -> "⚠" to fitColor(Fit.CAUTION)
        CriterionStatus.PROBLEM -> "✗" to fitColor(Fit.BAD)
        CriterionStatus.NOT_MEASURED, CriterionStatus.UNIT_NOT_COMPARED -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The mark is spoken through the reason (or « pas encore mesuré »), so colour and symbol never carry it alone.
    val spoken = listOfNotNull(label, detail, value, check.reason?.explanation()?.takeIf { it != detail }).joinToString(", ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, color = color, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            value,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun rangeText(range: ValueRange, formatter: LocalizedFormatters) =
    stringResource(Res.string.habitants_range, formatter.number(range.min, 1), formatter.number(range.max, 1))

/** Keeps a number with its unit at large font sizes. */
private const val NBSP = "\u00A0"

/** « Combien ? »: minus and plus are 48 dp each; the count stays within 1..[max]. */
@Composable
internal fun InhabitantCounter(count: Int, onChange: (Int) -> Unit, max: Int = Int.MAX_VALUE) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(count - 1) }, enabled = count > 1, modifier = Modifier.size(48.dp)) {
            Icon(AquagendaIcons.Minus, contentDescription = stringResource(Res.string.habitants_count_decrease))
        }
        Text(
            count.toString(),
            modifier = Modifier.widthIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onChange(count + 1) }, enabled = count < max, modifier = Modifier.size(48.dp)) {
            Icon(AquagendaIcons.Plus, contentDescription = stringResource(Res.string.habitants_count_increase))
        }
    }
}

@Composable
internal fun CyclingBanner() {
    Text(
        stringResource(Res.string.habitants_cycling),
        modifier = Modifier.fillMaxWidth().border(1.dp, attentionMarkColor(), RoundedCornerShape(8.dp)).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = attentionTextColor(),
    )
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Text(stringResource(Res.string.back))
    }
}

@Composable
internal fun fitColor(fit: Fit): Color = when (fit) {
    Fit.GOOD -> MaterialTheme.colorScheme.primary
    Fit.CAUTION -> levelTextColor(ReadingLevel.ATTENTION) ?: MaterialTheme.colorScheme.onSurface
    Fit.BAD -> levelTextColor(ReadingLevel.ALERT) ?: MaterialTheme.colorScheme.error
}

/** « ✓ Compatible », « ⚠ <raison> », « ✗ <raison> » or « ✗ N problèmes »: the badge always carries words. */
@Composable
internal fun badgeText(assessment: Assessment): String {
    val reason = assessment.badgeReason?.label()
    return when {
        assessment.problems >= 2 -> pluralStringResource(Res.plurals.habitants_badge_problems, assessment.problems, assessment.problems)
        assessment.fit == Fit.BAD && reason != null -> stringResource(Res.string.habitants_badge_bad, reason)
        assessment.fit == Fit.CAUTION && reason != null -> stringResource(Res.string.habitants_badge_caution, reason)
        else -> stringResource(Res.string.habitants_badge_good)
    }
}

/** The verdict's sentence: the first two reasons (problems first), else what was checked. */
@Composable
private fun explanation(assessment: Assessment): String {
    val parts = explanationReasons(assessment).map { it.explanation() }
    return when (parts.size) {
        0 -> stringResource(
            if (assessment.checks.any { it.status == CriterionStatus.NOT_MEASURED }) Res.string.habitants_explain_unmeasured else Res.string.habitants_explain_good,
        )
        1 -> stringResource(Res.string.habitants_explain_one, sentenceStart(parts[0]))
        else -> stringResource(Res.string.habitants_explain_two, sentenceStart(parts[0]), parts[1])
    }
}

@Composable
internal fun Reason.label(): String = stringResource(
    when (this) {
        Reason.TANK_TOO_SMALL -> Res.string.habitants_reason_tank_too_small
        Reason.WATER_TOO_HARD -> Res.string.habitants_reason_water_too_hard
        Reason.WATER_TOO_SOFT -> Res.string.habitants_reason_water_too_soft
        Reason.PH_TOO_HIGH -> Res.string.habitants_reason_ph_too_high
        Reason.PH_TOO_LOW -> Res.string.habitants_reason_ph_too_low
        Reason.TOO_WARM -> Res.string.habitants_reason_too_warm
        Reason.TOO_COLD -> Res.string.habitants_reason_too_cold
        Reason.EATS_SMALL_FISH -> Res.string.habitants_reason_eats_small_fish
        Reason.EATS_SHRIMP -> Res.string.habitants_reason_eats_shrimp
        Reason.FIN_NIPPING -> Res.string.habitants_reason_fin_nipping
        Reason.TERRITORIAL -> Res.string.habitants_reason_territorial
        Reason.EATS_PLANTS -> Res.string.habitants_reason_eats_plants
        Reason.GROUP_TOO_SMALL -> Res.string.habitants_reason_group_too_small
    },
)

/** Direction-neutral (cohabitation is checked both ways), so it fits whichever species is the hunter. */
@Composable
private fun Reason.explanation(): String = stringResource(
    when (this) {
        Reason.TANK_TOO_SMALL -> Res.string.habitants_explain_tank_too_small
        Reason.WATER_TOO_HARD -> Res.string.habitants_explain_water_too_hard
        Reason.WATER_TOO_SOFT -> Res.string.habitants_explain_water_too_soft
        Reason.PH_TOO_HIGH -> Res.string.habitants_explain_ph_too_high
        Reason.PH_TOO_LOW -> Res.string.habitants_explain_ph_too_low
        Reason.TOO_WARM -> Res.string.habitants_explain_too_warm
        Reason.TOO_COLD -> Res.string.habitants_explain_too_cold
        Reason.EATS_SMALL_FISH -> Res.string.habitants_explain_eats_small_fish
        Reason.EATS_SHRIMP -> Res.string.habitants_explain_eats_shrimp
        Reason.FIN_NIPPING -> Res.string.habitants_explain_fin_nipping
        Reason.TERRITORIAL -> Res.string.habitants_explain_territorial
        Reason.EATS_PLANTS -> Res.string.habitants_explain_eats_plants
        Reason.GROUP_TOO_SMALL -> Res.string.habitants_explain_group_too_small
    },
)
