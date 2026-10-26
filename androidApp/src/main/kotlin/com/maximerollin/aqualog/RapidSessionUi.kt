package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.evaluateMeasurement

@Composable
fun RapidSessionScreen(
    state: RapidSessionUiState,
    aquarium: Aquarium,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val formatter = currentLocalizedFormatters()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = viewModel::saveRapidSession,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .heightIn(min = 54.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            stringResource(
                                if (state.editingSessionId == null) R.string.save_session else R.string.save_changes,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val context = state.context
        if (context == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    stringResource(
                        if (state.editingSessionId == null) R.string.rapid_session_title else R.string.edit_session,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val focusManager = LocalFocusManager.current
        val listState = rememberLazyListState()
        LaunchedEffect(context.activeParameters.firstOrNull()?.id) {
            if (context.activeParameters.isNotEmpty()) {
                listState.scrollToItem(3)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("rapid-session-list").padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column {
                        Text(
                            stringResource(
                                if (state.editingSessionId == null) {
                                    R.string.rapid_session_title
                                } else {
                                    R.string.edit_session
                                },
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(aquarium.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = viewModel::closeRapidSession,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.cancel)) }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.session_aquarium, aquarium.name))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.shiftSessionTimeByMinutes(-15) }) {
                                Text(stringResource(R.string.earlier_time))
                            }
                            Text(
                                formatter.shortDateTime(state.occurredAtEpochMillis),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            TextButton(onClick = { viewModel.shiftSessionTimeByMinutes(15) }) {
                                Text(stringResource(R.string.later_time))
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.measurements_heading), style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(
                context.activeParameters,
                key = { _, definition -> definition.id },
            ) { index, definition ->
                val focusRequester = if (index == 0) remember(definition.id) { FocusRequester() } else null
                if (focusRequester != null) {
                    LaunchedEffect(definition.id) {
                        withFrameNanos { }
                        focusRequester.requestFocus()
                    }
                }
                val input = state.measurementInputs[definition.id].orEmpty()
                val feedback = evaluateMeasurement(input, definition)
                val description = stringResource(R.string.measurement_for_parameter, definition.parameter.label())
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
                        val stacked = LocalDensity.current.fontScale >= 1.3f || maxWidth < 280.dp
                        val contextLabel: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.parameter_with_unit, definition.parameter.label(), definition.unit),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val hint = when {
                                    !feedback.isValid -> stringResource(R.string.invalid_measurement)
                                    feedback.isOutsideIndicativeRange -> stringResource(R.string.unusual_measurement)
                                    context.lastMeasurements[definition.id] != null -> stringResource(
                                        R.string.last_measurement_compact,
                                        formatter.number(context.lastMeasurements.getValue(definition.id), definition.precision),
                                        definition.unit,
                                    )
                                    else -> stringResource(R.string.no_previous_measurement)
                                }
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!feedback.isValid) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val editor: @Composable () -> Unit = {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { viewModel.updateMeasurement(definition.id, it) },
                                isError = !feedback.isValid,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = if (index == context.activeParameters.lastIndex) ImeAction.Done else ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                    onDone = { focusManager.clearFocus() },
                                ),
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("rapid-measurement-${definition.parameter.storageValue}")
                                    .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
                                    .semantics {
                                        contentDescription = description
                                        stateDescription = definition.unit
                                    },
                            )
                        }
                        if (stacked) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                contextLabel()
                                editor()
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { contextLabel() }
                                Column(Modifier.width(112.dp)) { editor() }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.maintenance_heading), style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(MaintenanceActionType.entries) { _, type ->
                MaintenanceActionEditor(type, state, viewModel)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::toggleObservation) {
                        Text(
                            stringResource(
                                if (state.showObservation) R.string.hide_observation else R.string.add_observation,
                            ),
                        )
                    }
                    if (state.showObservation) {
                        OutlinedTextField(
                            value = state.observation,
                            onValueChange = viewModel::updateObservation,
                            label = { Text(stringResource(R.string.observation)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::toggleIncident) {
                        Text(
                            stringResource(
                                if (state.showIncident) R.string.hide_incident else R.string.add_incident,
                            ),
                        )
                    }
                    if (state.showIncident) {
                        OutlinedTextField(
                            value = state.incident,
                            onValueChange = viewModel::updateIncident,
                            label = { Text(stringResource(R.string.incident)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (state.saveError) {
                item { Text(stringResource(R.string.session_save_error), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MaintenanceActionEditor(
    type: MaintenanceActionType,
    state: RapidSessionUiState,
    viewModel: HomeViewModel,
) {
    val action = state.actions[type]
    val label = type.label()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = if (action != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = action != null,
                    role = Role.Checkbox,
                    onValueChange = { viewModel.toggleMaintenanceAction(type) },
                ).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = action != null, onCheckedChange = null)
                Text(label, modifier = Modifier.weight(1f).padding(8.dp))
                if (action != null) {
                    TextButton(onClick = { viewModel.editMaintenanceAction(type) }) {
                        Text(stringResource(R.string.edit_action))
                    }
                }
            }
        }
        if (action != null) {
            val summary = listOf(action.quantity, action.unit, action.product).filter(String::isNotBlank).joinToString(" ")
            if (summary.isNotBlank()) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && state.editingAction == type) {
            ActionDetails(action, viewModel)
        }
    }
}

@Composable
private fun ActionDetails(action: MaintenanceActionInput, viewModel: HomeViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = action.quantity,
            onValueChange = { viewModel.updateMaintenanceQuantity(action.type, it) },
            label = { Text(stringResource(R.string.action_quantity)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = action.unit,
            onValueChange = { viewModel.updateMaintenanceUnit(action.type, it) },
            label = { Text(stringResource(R.string.parameter_unit)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    if (action.type == MaintenanceActionType.FERTILIZATION) {
        OutlinedTextField(
            value = action.product,
            onValueChange = { viewModel.updateMaintenanceProduct(action.type, it) },
            label = { Text(stringResource(R.string.action_product)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun MaintenanceActionType.label(): String = stringResource(
    when (this) {
        MaintenanceActionType.WATER_CHANGE -> R.string.action_water_change
        MaintenanceActionType.FERTILIZATION -> R.string.action_fertilization
        MaintenanceActionType.FILTER_MAINTENANCE -> R.string.action_filter
        MaintenanceActionType.VACUUM -> R.string.action_vacuum
        MaintenanceActionType.GLASS_CLEANING -> R.string.action_glass
        MaintenanceActionType.PRUNING -> R.string.action_pruning
    },
)
