package com.maximerollin.aqualog.habitants

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.LocalizedFormatters
import com.maximerollin.aqualog.StatusBarOverHeader
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.encyclopedia.displayName
import com.maximerollin.aqualog.encyclopedia.localName
import com.maximerollin.aqualog.home.HeroText
import com.maximerollin.aqualog.home.TankScene
import com.maximerollin.aqualog.home.speciesDrawing
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
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
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import com.maximerollin.aqualog.shared.encyclopedia.ValueRange
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.ArrowLeft
import com.maximerollin.aqualog.ui.icon.Minus
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.FullScreenContentBacking
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import com.maximerollin.aqualog.ui.theme.levelTextColor
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One species judged against the Aquarium: its drawing on the water, the verdict card, facts and criteria in a list, and a bar
 * pinned to the bottom with the counter and the add button (never disabled by the verdict).
 */
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
    actions: AddInhabitantActions,
    modifier: Modifier = Modifier,
) {
    val assessment = remember(species, setup, readings, present, add.count) {
        assessSpecies(species, setup, readings, present, add.count)
    }
    var showDetails by rememberSaveable(species.id) { mutableStateOf(false) }
    val name = species.localName(language)
    val formatter = currentLocalizedFormatters()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // The list ends with this much room, so its last item scrolls clear of the bar laid over it.
    var barHeight by remember { mutableStateOf(0.dp) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize().testTag("species-fiche-list"), state = listState) {
            item(key = "header") { FicheHeader(species, setup.aquarium, name, actions.onBack, Modifier.overlapNext(30.dp)) }
            item(key = "sheet-top") { PaperSheetTop() }
            item(key = "verdict") { VerdictCard(assessment, setup.aquarium.name, Modifier.padding(horizontal = 16.dp)) }
            if (assessment.cyclingWarning) {
                item(key = "cycling") { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { CyclingBanner() } }
            }
            item(key = "facts") { FactTiles(species, formatter, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
            item(key = "compared") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GroupLabel(stringResource(Res.string.habitants_compared), Modifier.weight(1f))
                    if (assessment.fit == Fit.GOOD && !showDetails) {
                        Box(
                            Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(role = Role.Button) { showDetails = true }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(Res.string.habitants_details),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (assessment.fit != Fit.GOOD || showDetails) {
                item(key = "checks") {
                    SettingsGroup {
                        // COHABITATION may hold several checks, one per conflicting present species: each gets its row.
                        assessment.checks.forEach { check -> CheckRow(check, species, setup, inhabitants, encyclopedia, language, formatter) }
                    }
                }
            }
            if (assessment.checks.any { it.status == CriterionStatus.NOT_MEASURED }) {
                item(key = "measure") {
                    OutlinedButton(
                        onClick = actions.onMeasure,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.habitants_measure)) }
                }
            }
            item(key = "report") {
                TextButton(
                    onClick = { actions.onReportSpecies(species.id, name) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.feedback_report_species)) }
            }
            item(key = "bar-room") { Spacer(Modifier.height(barHeight)) }
        }
        StatusBarOverHeader(listState)
        FicheBottomBar(
            species, add, assessment.fit, name, actions,
            Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
    }
}

/** Height of the fiche's water; the paper sheet covers its bottom 30 dp. */
private val FicheHeaderHeight = 300.dp

/** The fade at the top of the name's backing, kept free of text: the name only lies on the solid part. */
private val NameFade = 48.dp

/**
 * The still water with the species' drawing (silent: the name says it), the glass back button under the status bar, and the
 * name with its scientific name on a deep backing at the bottom. It grows with large fonts rather than squeezing the name.
 */
@Composable
private fun FicheHeader(species: Species, aquarium: Aquarium, name: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (drawing, tint) = speciesDrawing(species)
    Box(modifier.fillMaxWidth().heightIn(min = FicheHeaderHeight)) {
        TankScene(aquarium, inhabitants = emptyList(), modifier = Modifier.matchParentSize(), decorPlants = 0, animate = false)
        Column(Modifier.fillMaxWidth().heightIn(min = FicheHeaderHeight)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                // Both sides bounded: with a width alone the painter keeps its small intrinsic height and Fit never scales up.
                Image(
                    painterResource(drawing), contentDescription = null, modifier = Modifier.width(200.dp).fillMaxHeight(),
                    contentScale = ContentScale.Fit, colorFilter = tint?.let { ColorFilter.tint(it) },
                )
            }
            Column(
                Modifier.fillMaxWidth()
                    .drawBehind {
                        val fade = NameFade.toPx().coerceAtMost(size.height)
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, FullScreenContentBacking), 0f, fade), size = Size(size.width, fade))
                        drawRect(FullScreenContentBacking, topLeft = Offset(0f, fade), size = Size(size.width, (size.height - fade).coerceAtLeast(0f)))
                    }
                    // Text starts below the fade and ends above the sheet that overlaps the bottom.
                    .padding(start = 20.dp, end = 20.dp, top = NameFade, bottom = 30.dp + 16.dp),
            ) {
                Text(
                    name,
                    modifier = Modifier.semantics { heading() },
                    fontFamily = frauncesFamily(),
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroText,
                )
                Text(
                    species.scientificName,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = HeroText.copy(alpha = .85f),
                )
            }
        }
        GlassIconButton(
            AquagendaIcons.ArrowLeft, stringResource(Res.string.back), onBack,
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, top = 4.dp),
        )
    }
}

/** The fit in words on its own colour, the mark beside it decorative, and why: one node, read as one sentence. */
@Composable
private fun VerdictCard(assessment: Assessment, aquariumName: String, modifier: Modifier = Modifier) {
    val color = fitColor(assessment.fit)
    Row(
        modifier.fillMaxWidth().background(fitFill(assessment.fit), RoundedCornerShape(20.dp)).padding(16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        FitMark(assessment.fit.markStatus())
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(fitVerdict(assessment.fit, aquariumName), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text(explanation(assessment), modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

/** Size, lifespan and group in a row of tiles (size only for a plant); stacked at large font scales. */
@Composable
private fun FactTiles(species: Species, formatter: LocalizedFormatters, modifier: Modifier = Modifier) {
    val facts = buildList {
        add(stringResource(Res.string.habitants_fact_size, formatter.number(species.adultSizeCm, 1)))
        if (species.kind != SpeciesKind.PLANT) {
            add(pluralStringResource(Res.plurals.habitants_fact_lifespan, species.lifespanYears, species.lifespanYears))
            add(stringResource(Res.string.habitants_fact_group, species.minGroup))
        }
    }
    if (LocalDensity.current.fontScale >= LargeFontScale) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { FactTile(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.forEach { FactTile(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun FactTile(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.background(colors.surface, shape).border(1.dp, colors.outlineVariant, shape).padding(12.dp)) {
        Text(text, fontSize = 14.sp, color = colors.onSurface)
    }
}

/**
 * Pinned under the list: the group minimum, the counter and the add button (its words wrap rather than cut), then the failure.
 * It takes the taps on it, so none falls through to the list under it.
 */
@Composable
private fun FicheBottomBar(
    species: Species,
    add: AddInhabitantUiState,
    fit: Fit,
    name: String,
    actions: AddInhabitantActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().testTag("fiche-bottom-bar")
            .pointerInput(Unit) {}
            .background(colors.surfaceContainer)
            .drawBehind { drawRect(colors.outlineVariant, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (species.minGroup > 1) {
            Text(
                stringResource(Res.string.habitants_count_minimum, species.minGroup),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InhabitantCounter(add.count, actions.onCountChange)
            Button(
                onClick = actions.onConfirm,
                enabled = !add.isSaving,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.padding(start = 8.dp).weight(1f).heightIn(min = 48.dp),
            ) {
                Text(
                    if (fit == Fit.BAD) {
                        stringResource(Res.string.habitants_add_anyway)
                    } else {
                        stringResource(Res.string.habitants_add_button, add.count, name)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (add.saveFailed) {
            Text(stringResource(Res.string.habitants_add_failed), modifier = Modifier.padding(top = 4.dp), color = colors.error)
        }
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
    // The mark is spoken through the reason (or « pas encore mesuré »), so colour and symbol never carry it alone.
    val spoken = listOfNotNull(label, detail, value, check.reason?.explanation()?.takeIf { it != detail })
        .filter { it.isNotBlank() }.joinToString(", ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitMark(check.status)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
        Reason.EATS_SNAILS -> Res.string.habitants_reason_eats_snails
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
        Reason.EATS_SNAILS -> Res.string.habitants_explain_eats_snails
        Reason.FIN_NIPPING -> Res.string.habitants_explain_fin_nipping
        Reason.TERRITORIAL -> Res.string.habitants_explain_territorial
        Reason.EATS_PLANTS -> Res.string.habitants_explain_eats_plants
        Reason.GROUP_TOO_SMALL -> Res.string.habitants_explain_group_too_small
    },
)
