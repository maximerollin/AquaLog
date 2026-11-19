package com.maximerollin.aqualog.habitants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.StatusBarOverHeader
import com.maximerollin.aqualog.encyclopedia.localName
import com.maximerollin.aqualog.home.aquariumContextLine
import com.maximerollin.aqualog.home.speciesDrawing
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.FeedbackKind
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.LatestReading
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.Species
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.ArrowLeft
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.SearchPill
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.SettingsRow
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

/** « Ajouter un habitant »: the search, a species fiche, or the custom form, for the Aquarium it was opened on. */
@Composable
fun AddInhabitantScreen(add: AddInhabitantUiState, state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val setup = state.setup ?: return
    val language = remember { Locale.getDefault().language }
    val encyclopedia = add.species.orEmpty()
    val present = remember(state.inhabitants, encyclopedia) {
        state.inhabitants.map { inhabitant ->
            PresentGroup(encyclopedia.firstOrNull { it.id == inhabitant.speciesId }, inhabitant.family, inhabitant.count)
        }
    }
    val actions = remember(viewModel) {
        AddInhabitantActions(
            onBack = viewModel::backInAddInhabitant,
            onQueryChange = viewModel::updateSpeciesQuery,
            onSelectKind = viewModel::selectSpeciesKind,
            onOpenFiche = viewModel::openSpeciesFiche,
            onOpenCustom = viewModel::openCustomInhabitant,
            onCountChange = viewModel::changeInhabitantCount,
            onCustomNameChange = viewModel::updateCustomInhabitantName,
            onSelectFamily = viewModel::selectCustomInhabitantFamily,
            onConfirm = viewModel::confirmAddInhabitant,
            onMeasure = { viewModel.openRapidSession() },
            onReportSpecies = { speciesId, name -> viewModel.openFeedback(FeedbackKind.SPECIES, speciesId, name) },
        )
    }
    AddInhabitantContent(add, setup, state.latestReadings, state.inhabitants, present, encyclopedia, language, actions, modifier)
}

/** What « Ajouter un habitant » asks for; [AddInhabitantScreen] wires them to the [HomeViewModel], a test to fakes. */
internal class AddInhabitantActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSelectKind: (SpeciesKind?) -> Unit = {},
    val onOpenFiche: (speciesId: String) -> Unit = {},
    val onOpenCustom: () -> Unit = {},
    val onCountChange: (Int) -> Unit = {},
    val onCustomNameChange: (String) -> Unit = {},
    val onSelectFamily: (DrawingFamily) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onMeasure: () -> Unit = {},
    val onReportSpecies: (speciesId: String, name: String) -> Unit = { _, _ -> },
)

/** The fiche of the chosen species, the custom form, or the search, from the state alone. */
@Composable
internal fun AddInhabitantContent(
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
    val fiche = add.ficheSpecies
    when {
        fiche != null -> SpeciesFiche(fiche, add, setup, readings, inhabitants, present, encyclopedia, language, actions, modifier)
        add.custom -> CustomInhabitantForm(add, setup.aquarium, actions, modifier)
        else -> SpeciesSearch(add, setup, readings, inhabitants, present, language, actions, modifier)
    }
}

/** The water header of both screens, its back button on the left; the paper sheet that follows covers its bottom. */
private fun LazyListScope.header(aquarium: Aquarium, title: @Composable () -> String, subtitle: @Composable () -> String, onBack: () -> Unit) {
    item(key = "header") {
        WaterHeader(
            aquarium = aquarium,
            title = title(),
            subtitle = subtitle(),
            leading = { GlassIconButton(AquagendaIcons.ArrowLeft, stringResource(Res.string.back), onBack) },
            modifier = Modifier.overlapNext(30.dp),
        )
    }
    item(key = "sheet-top") { PaperSheetTop() }
}

@Composable
private fun selectedPrimaryChip() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun SpeciesSearch(
    add: AddInhabitantUiState,
    setup: AquariumSetup,
    readings: List<LatestReading>,
    inhabitants: List<Inhabitant>,
    present: List<PresentGroup>,
    language: String,
    actions: AddInhabitantActions,
    modifier: Modifier,
) {
    val groups = remember(add.species, add.query, add.kind, setup, readings, present, language) {
        groupedForAquarium(
            searchSpecies(add.species.orEmpty(), add.query, language, add.kind).map { assessSpecies(it, setup, readings, present) },
            language,
        )
    }
    val aquarium = setup.aquarium
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("add-inhabitant-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            header(
                aquarium,
                title = { stringResource(Res.string.habitants_add_one) },
                subtitle = { aquariumContextLine(aquarium, inhabitants.sumOf { it.count }) },
                onBack = actions.onBack,
            )
            item(key = "search") {
                SpeciesSearchField(add.query, actions.onQueryChange, Modifier.padding(horizontal = 16.dp))
            }
            item(key = "kinds") {
                FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesKind.entries.forEach { kind ->
                        FilterChip(
                            selected = add.kind == kind,
                            onClick = { actions.onSelectKind(kind.takeIf { add.kind != it }) },
                            label = { Text(kind.label()) },
                            colors = selectedPrimaryChip(),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            when {
                add.loadFailed -> item(key = "unavailable") {
                    Text(
                        stringResource(Res.string.habitants_unavailable),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                add.species == null -> item(key = "loading") {
                    CircularProgressIndicator(Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
            groups.forEach { (fit, assessments) ->
                item(key = "label-${fit.name}") {
                    GroupLabel(
                        stringResource(
                            when (fit) {
                                Fit.GOOD -> Res.string.habitants_group_good
                                Fit.CAUTION -> Res.string.habitants_group_caution
                                Fit.BAD -> Res.string.habitants_group_bad
                            },
                        ),
                        Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                item(key = "group-${fit.name}") {
                    SettingsGroup {
                        assessments.forEach { assessment ->
                            key(assessment.species.id) {
                                SpeciesRow(assessment, language) { actions.onOpenFiche(assessment.species.id) }
                            }
                        }
                    }
                }
            }
            item(key = "missing") {
                SettingsGroup(Modifier.padding(top = 16.dp)) {
                    SettingsRow(stringResource(Res.string.habitants_missing), AquagendaIcons.Plus, onClick = actions.onOpenCustom)
                }
            }
        }
        StatusBarOverHeader(listState)
    }
}

/** The search box: a [SearchPill] named by its hint. */
@Composable
internal fun SpeciesSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    SearchPill(query, onQueryChange, stringResource(Res.string.habitants_search_hint), modifier, testTag = "species-search")
}

/** Thumbnail, names and the fit pill: one button that opens the fiche. Large text puts the pill under the names. */
@Composable
private fun SpeciesRow(assessment: Assessment, language: String, onClick: () -> Unit) {
    // On its own line, a long name (« Spitzmaulkärpfling ») never breaks mid-word for the pill.
    val stacked = LocalDensity.current.fontScale >= LargeFontScale
    val colors = MaterialTheme.colorScheme
    val (drawing, tint) = speciesDrawing(assessment.species)
    val pillText = fitPillText(assessment)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesThumb(drawing, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(assessment.species.localName(language), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(assessment.species.scientificName, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
            if (stacked) FitPill(pillText, assessment.fit, Modifier.padding(top = 6.dp))
        }
        // The pill takes its words' width, up to a cap; the names get the rest of the line.
        if (!stacked) FitPill(pillText, assessment.fit, Modifier.padding(start = 8.dp).widthIn(max = 140.dp))
    }
}

/** A species outside the encyclopedia: a typed name and a family silhouette, no verdict. */
@Composable
private fun CustomInhabitantForm(add: AddInhabitantUiState, aquarium: Aquarium, actions: AddInhabitantActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp + bottomInset)) {
            header(aquarium, title = { stringResource(Res.string.habitants_custom_title) }, subtitle = { aquarium.name }, onBack = actions.onBack)
            item(key = "form") { CustomInhabitantFields(add, aquarium, actions) }
        }
        StatusBarOverHeader(listState)
    }
}

@Composable
private fun CustomInhabitantFields(add: AddInhabitantUiState, aquarium: Aquarium, actions: AddInhabitantActions) {
    val error = stringResource(Res.string.habitants_custom_error)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            FilledField(
                value = add.customName,
                onValueChange = actions.onCustomNameChange,
                label = stringResource(Res.string.habitants_custom_name),
                isError = add.showCustomError,
                errorMessage = error,
            )
            if (add.showCustomError) Text(error, Modifier.padding(start = 4.dp, top = 6.dp), color = MaterialTheme.colorScheme.error)
        }
        GroupLabel(stringResource(Res.string.habitants_custom_family), Modifier.padding(top = 8.dp))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingFamily.entries.forEach { family ->
                FilterChip(
                    selected = add.customFamily == family,
                    onClick = { actions.onSelectFamily(family) },
                    label = { Text(family.label()) },
                    colors = selectedPrimaryChip(),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        val family = add.customFamily
        if (aquarium.profile == AquariumProfile.CYCLING && family != null && family !in notFish) {
            Box(Modifier.padding(horizontal = 16.dp)) { CyclingBanner() }
        }
        Column {
            GroupLabel(stringResource(Res.string.habitants_count_title), Modifier.padding(top = 8.dp))
            Box(Modifier.padding(horizontal = 8.dp)) { InhabitantCounter(add.count, actions.onCountChange) }
        }
        Button(
            onClick = actions.onConfirm,
            enabled = !add.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.habitants_custom_add)) }
        if (add.saveFailed) {
            Text(
                stringResource(Res.string.habitants_add_failed),
                Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val notFish = setOf(DrawingFamily.SHRIMP, DrawingFamily.SNAIL, DrawingFamily.PLANT)

@Composable
private fun SpeciesKind.label(): String = stringResource(
    when (this) {
        SpeciesKind.FISH -> Res.string.habitants_kind_fish
        SpeciesKind.SHRIMP -> Res.string.habitants_kind_shrimp
        SpeciesKind.SNAIL -> Res.string.habitants_kind_snail
        SpeciesKind.PLANT -> Res.string.habitants_kind_plant
    },
)

@Composable
private fun DrawingFamily.label(): String = stringResource(
    when (this) {
        DrawingFamily.TETRA -> Res.string.habitants_family_tetra
        DrawingFamily.LIVEBEARER -> Res.string.habitants_family_livebearer
        DrawingFamily.CICHLID -> Res.string.habitants_family_cichlid
        DrawingFamily.LABYRINTH -> Res.string.habitants_family_labyrinth
        DrawingFamily.CYPRINID -> Res.string.habitants_family_cyprinid
        DrawingFamily.CATFISH -> Res.string.habitants_family_catfish
        DrawingFamily.SHRIMP -> Res.string.habitants_family_shrimp
        DrawingFamily.SNAIL -> Res.string.habitants_family_snail
        DrawingFamily.PLANT -> Res.string.habitants_family_plant
    },
)
