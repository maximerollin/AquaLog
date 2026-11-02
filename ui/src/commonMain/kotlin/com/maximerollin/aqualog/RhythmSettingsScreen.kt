package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.NEVER_INTERVAL_DAYS
import com.maximerollin.aqualog.shared.assistance.defaultIntervalDays
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Stored choices: null is « Auto (profil) », [NEVER_INTERVAL_DAYS] is « Jamais ». */
private val RHYTHM_CHOICES: List<Int?> = listOf(null, 1, 2, 3, 7, 14, 30, NEVER_INTERVAL_DAYS)

/** « Rythme d'analyse » of the current Aquarium: one row per active parameter, each opening its choices. */
@Composable
fun RhythmSettingsScreen(setup: AquariumSetup, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = setup.aquarium.profile
    val parameters = setup.parameters.filter { it.isActive }.sortedBy { it.position }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            CarnetScreenTitle(
                stringResource(Res.string.rhythm_title),
                setup.aquarium.name,
                Modifier.padding(top = 12.dp, bottom = 8.dp),
                action = {
                    TextButton(
                        onClick = viewModel::closeRhythmSettings,
                        modifier = Modifier.requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text(stringResource(Res.string.back)) }
                },
            )
        }
        items(parameters, key = { it.id }) { definition ->
            Column {
                CarnetDivider()
                MoreRow(
                    definition.parameter.label(),
                    supporting = rhythmLabel(definition.analysisIntervalDays, definition, profile),
                    onClick = { editingId = definition.id },
                )
            }
        }
    }
    parameters.firstOrNull { it.id == editingId }?.let { definition ->
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(definition.parameter.label()) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    RHYTHM_CHOICES.forEach { days ->
                        val selected = definition.analysisIntervalDays == days
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton) {
                                editingId = null
                                viewModel.updateAnalysisInterval(definition.id, days)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(rhythmLabel(days, definition, profile))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingId = null }) { Text(stringResource(Res.string.cancel)) } },
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
