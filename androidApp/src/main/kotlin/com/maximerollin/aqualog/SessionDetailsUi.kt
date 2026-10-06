package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.RecordedSession
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionDetailsScreen(
    recorded: RecordedSession,
    setup: AquariumSetup,
    showDeleteConfirmation: Boolean,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val parametersById = setup.parameters.associateBy { it.id }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.session_details), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = viewModel::closeSessionDetails) {
                    Text(stringResource(R.string.back))
                }
            }
        }
        item {
            Text(
                stringResource(
                    R.string.session_date,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(recorded.session.occurredAtEpochMillis)),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recorded.measurements.forEach { measurement ->
            val definition = parametersById[measurement.parameterDefinitionId]
            if (definition != null) {
                item(measurement.id) {
                    Text(
                        stringResource(
                            R.string.session_measurement_detail,
                            definition.parameter.label(),
                            measurement.value.displaySessionValue(),
                            definition.unit,
                        ),
                    )
                }
            }
        }
        recorded.maintenanceActions.forEach { action ->
            item(action.id) {
                val detail = listOf(
                    action.quantity?.displaySessionValue().orEmpty(),
                    action.unit.orEmpty(),
                    action.product.orEmpty(),
                ).filter(String::isNotBlank).joinToString(" ")
                Text(stringResource(R.string.session_action_detail, action.type.label(), detail))
            }
        }
        recorded.events.forEach { event ->
            item(event.id) {
                Text(
                    stringResource(
                        R.string.session_event_detail,
                        stringResource(
                            if (event.type == EventType.OBSERVATION) R.string.observation else R.string.incident,
                        ),
                        event.note,
                    ),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::editSession,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.edit_session))
                }
                OutlinedButton(
                    onClick = viewModel::requestSessionDeletion,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.delete_session))
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelSessionDeletion,
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_description)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSessionDeletion) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelSessionDeletion) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun Double.displaySessionValue(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
