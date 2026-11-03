package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.assistance.MeasurementConcern
import com.maximerollin.aqualog.shared.toEpochDay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Never blocks: the person can fix, save anyway or go back (spec 4.3.2). */
@Composable
fun SaveCheckDialog(state: RapidSessionUiState, viewModel: HomeViewModel) {
    if (state.pendingConcerns.isEmpty()) return
    val formatter = currentLocalizedFormatters()
    val definitions = state.context?.activeParameters.orEmpty().filter { it.id in state.pendingConcerns }
    AlertDialog(
        onDismissRequest = viewModel::dismissConcerns,
        title = { Text(stringResource(Res.string.check_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                definitions.forEach { definition ->
                    val name = definition.parameter.label()
                    val entered = formatter.number(
                        com.maximerollin.aqualog.shared.parseDecimalInput(state.measurementInputs[definition.id].orEmpty()) ?: 0.0,
                    )
                    when (val concern = state.pendingConcerns.getValue(definition.id)) {
                        is MeasurementConcern.Implausible -> Column {
                            Text(stringResource(Res.string.check_implausible, name, entered))
                            concern.suggestion?.let { suggestion ->
                                Text(
                                    stringResource(
                                        Res.string.check_implausible_suggestion,
                                        formatter.number(suggestion, definition.precision),
                                    ),
                                )
                                TextButton(
                                    onClick = { viewModel.applySuggestion(definition.id) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(Res.string.check_fix)) }
                            }
                        }
                        is MeasurementConcern.BigJump -> {
                            val previous = formatter.number(concern.previous, definition.precision)
                            // Calendar days on the VM clock, like the home's « il y a N jours ».
                            val days = (viewModel.today().toEpochDay() - viewModel.dayOf(concern.previousAtEpochMillis).toEpochDay()).toInt()
                            Text(
                                if (days <= 0) {
                                    stringResource(Res.string.check_big_jump_today, name, entered, previous)
                                } else {
                                    pluralStringResource(Res.plurals.check_big_jump, days, name, entered, previous, days)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.saveRapidSession(confirmed = true) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.check_save_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissConcerns, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.check_back))
            }
        },
    )
}
