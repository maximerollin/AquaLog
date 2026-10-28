package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun TaskScreen(state: TaskUiState, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val formatter = currentLocalizedFormatters()
    val requestNotifications = rememberNotificationPermissionRequest()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("task-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.tasks_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(
                    onClick = viewModel::closeTasks,
                    modifier = Modifier.testTag("tasks-back").requiredSizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(Res.string.back))
                }
            }
        }
        if (requestNotifications != null) {
            item {
                ReminderInformationCard(stringResource(Res.string.notification_permission_explanation)) {
                    Button(
                        onClick = requestNotifications,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.allow_notifications)) }
                }
            }
        }
        if (state.batteryOptimizationActive) {
            item { ReminderInformationCard(stringResource(Res.string.battery_optimization_explanation)) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.new_task), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTaskTitle,
                        label = { Text(stringResource(Res.string.task_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val selectedDate = runCatching { LocalDate.parse(state.dueDate) }
                        .getOrElse { LocalDate.now() }
                    OutlinedButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Column {
                            Text(stringResource(Res.string.task_due_date_label))
                            Text(
                                formatter.date(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    val selectedTime = runCatching { LocalTime.parse(state.time) }
                        .getOrElse { LocalTime.of(9, 0) }
                    OutlinedButton(
                        onClick = { pickingTime = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Column {
                            Text(stringResource(Res.string.task_time_label))
                            Text(
                                state.time.toMinuteOfDayOrNull()?.let(formatter::time)
                                    ?: stringResource(Res.string.task_no_time),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (pickingDate) {
                        TaskDatePickerDialog(
                            selectedDate = selectedDate,
                            onDateSelected = { viewModel.updateTaskDueDate(it.toString()) },
                            onDismiss = { pickingDate = false },
                        )
                    }
                    if (pickingTime) {
                        TaskTimePickerDialog(
                            selectedTime = selectedTime,
                            onTimeSelected = { viewModel.updateTaskTime("%02d:%02d".format(it.hour, it.minute)) },
                            onDismiss = { pickingTime = false },
                        )
                    }
                    if (state.time.isNotBlank()) {
                        TextButton(
                            onClick = { viewModel.updateTaskTime("") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(Res.string.clear_task_time))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TaskRecurrence.entries.forEach { recurrence ->
                            FilterChip(
                                selected = state.recurrence == recurrence,
                                onClick = { viewModel.selectTaskRecurrence(recurrence) },
                                label = { Text(recurrence.label()) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    if (state.showValidationError) {
                        Text(stringResource(Res.string.task_validation_error), color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = viewModel::createTask,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(Res.string.save_task)) }
                }
            }
        }
        item { Text(stringResource(Res.string.pending_tasks), style = MaterialTheme.typography.titleLarge) }
        if (state.pendingOccurrences.isEmpty()) {
            item { Text(stringResource(Res.string.no_pending_tasks)) }
        } else {
            items(state.pendingOccurrences, key = TaskOccurrence::id) { occurrence ->
                TaskOccurrenceCard(occurrence, viewModel)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskDatePickerDialog(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // Material3 date pickers work in UTC midnight milliseconds.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDateSelected(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(Res.string.picker_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        DatePicker(state, Modifier.testTag("task-date-picker"))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TaskTimePickerDialog(selectedTime: LocalTime, onTimeSelected: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(selectedTime.hour, selectedTime.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(Res.string.picker_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = { TimePicker(state, Modifier.testTag("task-time-picker")) },
    )
}

@Composable
private fun ReminderInformationCard(message: String, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@Composable
private fun TaskOccurrenceCard(occurrence: TaskOccurrence, viewModel: HomeViewModel) {
    val formatter = currentLocalizedFormatters()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(occurrence.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    Res.string.task_due_value,
                    formatter.date(occurrence.dueDate.year, occurrence.dueDate.month, occurrence.dueDate.day),
                    occurrence.minuteOfDay?.let(formatter::time) ?: stringResource(Res.string.task_no_time),
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.COMPLETED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.task_complete)) }
                OutlinedButton(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.POSTPONED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.task_postpone)) }
                TextButton(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.IGNORED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(Res.string.task_ignore)) }
            }
        }
    }
}

@Composable
private fun TaskRecurrence.label(): String = stringResource(
    when (this) {
        TaskRecurrence.ONCE -> Res.string.task_once
        TaskRecurrence.DAILY -> Res.string.task_daily
        TaskRecurrence.WEEKLY -> Res.string.task_weekly
        TaskRecurrence.MONTHLY -> Res.string.task_monthly
    },
)

internal fun com.maximerollin.aqualog.shared.TaskDate.toIsoText(): String =
    "%04d-%02d-%02d".format(year, month, day)

private fun String.toMinuteOfDayOrNull(): Int? {
    val parts = split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
