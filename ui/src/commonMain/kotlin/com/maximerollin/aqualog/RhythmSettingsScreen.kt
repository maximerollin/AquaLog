package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.NEVER_INTERVAL_DAYS
import com.maximerollin.aqualog.shared.assistance.defaultIntervalDays
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Stored choices: null is « Auto (profil) », [NEVER_INTERVAL_DAYS] is « Jamais ». */
private val RHYTHM_CHOICES: List<Int?> = listOf(null, 1, 2, 3, 7, 14, 30, NEVER_INTERVAL_DAYS)

/** « Rythme d'analyse » of the current Aquarium: one row per active parameter, each expanding in place to its choices. */
@Composable
fun RhythmSettingsScreen(setup: AquariumSetup, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    RhythmContent(setup, viewModel::updateAnalysisInterval, viewModel::closeRhythmSettings, modifier)
}

/** The stateless screen: [onUpdate] gets a parameter id and its chosen interval, [onClose] is Back. */
@Composable
internal fun RhythmContent(
    setup: AquariumSetup,
    onUpdate: (parameterId: String, days: Int?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    // While a row is open, Back only folds it, as Back once closed the choice dialog; composed after the screen's own handler,
    // it takes Back first. The next Back leaves the screen.
    SystemBackHandler(enabled = expandedId != null) { expandedId = null }
    val profile = setup.aquarium.profile
    val parameters = setup.parameters.filter { it.isActive }.sortedBy { it.position }
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("rhythm-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = setup.aquarium,
                    title = stringResource(Res.string.rhythm_title),
                    subtitle = setup.aquarium.name,
                    leading = {
                        GlassIconButton(
                            AquagendaIcons.ArrowLeft, stringResource(Res.string.back), onClose,
                            Modifier.testTag("rhythm-back"),
                        )
                    },
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            item(key = "description") {
                Text(
                    stringResource(Res.string.rhythm_description),
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "parameters") {
                SettingsGroup {
                    parameters.forEach { definition ->
                        key(definition.id) {
                            val expanded = definition.id == expandedId
                            // One top-level node per parameter: the row and its choices are one row of the group.
                            Column {
                                RhythmRow(
                                    label = definition.parameter.label(),
                                    current = rhythmLabel(definition.analysisIntervalDays, definition, profile),
                                    expanded = expanded,
                                    onClick = { expandedId = if (expanded) null else definition.id },
                                )
                                if (expanded) {
                                    FlowRow(
                                        Modifier.fillMaxWidth().selectableGroup().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        RHYTHM_CHOICES.forEach { days ->
                                            RhythmChoice(
                                                label = rhythmLabel(days, definition, profile),
                                                selected = definition.analysisIntervalDays == days,
                                                onClick = {
                                                    expandedId = null
                                                    onUpdate(definition.id, days)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        StatusBarOverHeader(listState)
    }
}

/** A parameter's line: its label, a chip with the current choice (filled while expanded) and a chevron; one button. */
@Composable
private fun RhythmRow(label: String, current: String, expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = stringResource(if (expanded) Res.string.trends_row_expanded else Res.string.trends_row_collapsed)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(
                current,
                // Capped: a long value wraps within 45 % of the line, so the label keeps the rest.
                modifier = Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * .45f).clip(CircleShape)
                    .background(if (expanded) colors.primary else colors.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (expanded) colors.onPrimary else colors.onSurfaceVariant,
            )
            Icon(
                if (expanded) AquagendaIcons.ChevronDown else AquagendaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One choice: a chip at least 48 dp tall; the selected one is filled and carries a check, so colour is not alone. */
@Composable
private fun RhythmChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(AquagendaIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
private fun rhythmLabel(days: Int?, definition: ParameterDefinition, profile: AquariumProfile): String = when (days) {
    null -> defaultIntervalDays(profile, definition.parameter)
        ?.let { pluralStringResource(Res.plurals.rhythm_auto_days, it, it) }
        ?: stringResource(Res.string.rhythm_auto_never)
    NEVER_INTERVAL_DAYS -> stringResource(Res.string.rhythm_never)
    else -> pluralStringResource(Res.plurals.rhythm_days, days, days)
}
