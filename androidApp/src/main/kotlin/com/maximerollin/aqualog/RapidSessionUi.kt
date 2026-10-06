package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.evaluateMeasurement
import java.text.DateFormat
import java.util.Date

@Composable
fun RapidSessionScreen(
    state: RapidSessionUiState,
    aquarium: Aquarium,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = viewModel::saveRapidSession,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        .height(48.dp),
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    TextButton(onClick = viewModel::closeRapidSession) { Text(stringResource(R.string.cancel)) }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.session_aquarium, aquarium.name))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.shiftSessionTimeByMinutes(-15) }) {
                                Text(stringResource(R.string.earlier_time))
                            }
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(state.occurredAtEpochMillis)),
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
                OutlinedTextField(
                    value = input,
                    onValueChange = { viewModel.updateMeasurement(definition.id, it) },
                    label = { Text("${definition.parameter.label()} (${definition.unit})") },
                    supportingText = {
                        when {
                            !feedback.isValid -> Text(stringResource(R.string.invalid_measurement))
                            feedback.isOutsideIndicativeRange -> Text(stringResource(R.string.unusual_measurement))
                            context.lastMeasurements[definition.id] != null -> Text(
                                stringResource(
                                    R.string.last_measurement,
                                    context.lastMeasurements.getValue(definition.id).displayValue(definition.precision),
                                    definition.unit,
                                ),
                            )
                            else -> Text(stringResource(R.string.no_previous_measurement))
                        }
                    },
                    isError = !feedback.isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (index == context.activeParameters.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
                        .semantics { contentDescription = description },
                )
            }
            item { Text(stringResource(R.string.maintenance_heading), style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(MaintenanceActionType.entries) { _, type ->
                MaintenanceActionEditor(type, state, viewModel)
            }
            item {
                TextButton(onClick = viewModel::toggleObservation) {
                    Text(stringResource(if (state.showObservation) R.string.hide_observation else R.string.add_observation))
                }
            }
            if (state.showObservation) {
                item {
                    OutlinedTextField(
                        value = state.observation,
                        onValueChange = viewModel::updateObservation,
                        label = { Text(stringResource(R.string.observation)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                TextButton(onClick = viewModel::toggleIncident) {
                    Text(stringResource(if (state.showIncident) R.string.hide_incident else R.string.add_incident))
                }
            }
            if (state.showIncident) {
                item {
                    OutlinedTextField(
                        value = state.incident,
                        onValueChange = viewModel::updateIncident,
                        label = { Text(stringResource(R.string.incident)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = action != null,
                onClick = { viewModel.toggleMaintenanceAction(type) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                TextButton(onClick = { viewModel.editMaintenanceAction(type) }) {
                    Text(stringResource(R.string.edit_action))
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

private fun Double.displayValue(precision: Int): String = "%1$.${precision}f".format(this)
