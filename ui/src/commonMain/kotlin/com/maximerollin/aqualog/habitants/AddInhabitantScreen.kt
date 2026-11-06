package com.maximerollin.aqualog.habitants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.HomeUiState
import com.maximerollin.aqualog.HomeViewModel
import com.maximerollin.aqualog.MoreRow
import com.maximerollin.aqualog.encyclopedia.localName
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.encyclopedia.Assessment
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.encyclopedia.Fit
import com.maximerollin.aqualog.shared.encyclopedia.PresentGroup
import com.maximerollin.aqualog.shared.encyclopedia.SpeciesKind
import com.maximerollin.aqualog.shared.encyclopedia.assessSpecies
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.ChevronRight
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
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
    val fiche = add.ficheSpecies
    when {
        fiche != null -> SpeciesFiche(
            fiche, add, setup, state.latestReadings, state.inhabitants, present, encyclopedia, language, viewModel, modifier,
        )
        add.custom -> CustomInhabitantForm(add, setup, viewModel, modifier)
        else -> SpeciesSearch(add, setup, state, present, language, viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSearch(
    add: AddInhabitantUiState,
    setup: AquariumSetup,
    state: HomeUiState,
    present: List<PresentGroup>,
    language: String,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    val groups = remember(add.species, add.query, add.kind, setup, state.latestReadings, present, language) {
        groupedForAquarium(
            searchSpecies(add.species.orEmpty(), add.query, language, add.kind).map { assessSpecies(it, setup, state.latestReadings, present) },
            language,
        )
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            CarnetScreenTitle(
                stringResource(Res.string.habitants_add_title, setup.aquarium.name),
                null,
                Modifier.padding(top = 12.dp, bottom = 8.dp),
                action = { BackButton(viewModel::backInAddInhabitant) },
            )
        }
        item {
            OutlinedTextField(
                value = add.query,
                onValueChange = viewModel::updateSpeciesQuery,
                placeholder = { Text(stringResource(Res.string.habitants_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesKind.entries.forEach { kind ->
                    FilterChip(
                        selected = add.kind == kind,
                        onClick = { viewModel.selectSpeciesKind(kind.takeIf { add.kind != it }) },
                        label = { Text(kind.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        when {
            add.loadFailed -> item {
                Text(
                    stringResource(Res.string.habitants_unavailable),
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            add.species == null -> item { CircularProgressIndicator(Modifier.padding(vertical = 12.dp)) }
        }
        groups.forEach { (fit, assessments) ->
            item(key = fit.name) {
                CarnetSectionTitle(
                    stringResource(
                        when (fit) {
                            Fit.GOOD -> Res.string.habitants_group_good
                            Fit.CAUTION -> Res.string.habitants_group_caution
                            Fit.BAD -> Res.string.habitants_group_bad
                        },
                    ),
                    Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(assessments, key = { it.species.id }) { assessment ->
                Column {
                    CarnetDivider()
                    SpeciesRow(assessment, language) { viewModel.openSpeciesFiche(assessment.species.id) }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                CarnetDivider()
                MoreRow(
                    stringResource(Res.string.habitants_missing),
                    onClick = viewModel::openCustomInhabitant,
                    trailingIcon = AquagendaIcons.ChevronRight,
                )
                CarnetDivider()
            }
        }
    }
}

@Composable
private fun SpeciesRow(assessment: Assessment, language: String, onClick: () -> Unit) {
    // Large text puts the badge on its own line, so a long name (« Spitzmaulkärpfling ») never breaks mid-word.
    val stacked = LocalDensity.current.fontScale > 1.3f
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp)
    val names = @Composable {
        Text(assessment.species.localName(language), style = MaterialTheme.typography.bodyLarge)
        Text(
            assessment.species.scientificName,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val badge = @Composable { modifier: Modifier, align: TextAlign ->
        Text(badgeText(assessment), modifier, style = MaterialTheme.typography.labelLarge, color = fitColor(assessment.fit), textAlign = align)
    }
    if (stacked) {
        Column(rowModifier) {
            names()
            badge(Modifier.padding(top = 4.dp), TextAlign.Start)
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { names() }
            badge(Modifier.padding(start = 12.dp).weight(1f, fill = false), TextAlign.End)
        }
    }
}

/** A species outside the encyclopedia: a typed name and a family silhouette, no verdict. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomInhabitantForm(add: AddInhabitantUiState, setup: AquariumSetup, viewModel: HomeViewModel, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CarnetScreenTitle(
            stringResource(Res.string.habitants_custom_title),
            setup.aquarium.name,
            Modifier.padding(top = 12.dp),
            action = { BackButton(viewModel::backInAddInhabitant) },
        )
        OutlinedTextField(
            value = add.customName,
            onValueChange = viewModel::updateCustomInhabitantName,
            label = { Text(stringResource(Res.string.habitants_custom_name)) },
            isError = add.showCustomError && add.customName.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CarnetSectionTitle(stringResource(Res.string.habitants_custom_family))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawingFamily.entries.forEach { family ->
                FilterChip(
                    selected = add.customFamily == family,
                    onClick = { viewModel.selectCustomInhabitantFamily(family) },
                    label = { Text(family.label()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        val family = add.customFamily
        if (setup.aquarium.profile == AquariumProfile.CYCLING && family != null && family !in notFish) CyclingBanner()
        Column {
            CarnetSectionTitle(stringResource(Res.string.habitants_count_title))
            InhabitantCounter(add.count, viewModel::changeInhabitantCount)
        }
        if (add.showCustomError) Text(stringResource(Res.string.habitants_custom_error), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = viewModel::confirmAddInhabitant,
            enabled = !add.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.habitants_custom_add)) }
        if (add.saveFailed) Text(stringResource(Res.string.habitants_add_failed), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
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
