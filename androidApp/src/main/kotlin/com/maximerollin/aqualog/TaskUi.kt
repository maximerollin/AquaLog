package com.maximerollin.aqualog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution

@Composable
fun TaskScreen(state: TaskUiState, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val notificationPermissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("task-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tasks_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = viewModel::closeTasks, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.back))
                }
            }
        }
        if (notificationPermissionMissing) {
            item {
                ReminderInformationCard(stringResource(R.string.notification_permission_explanation)) {
                    Button(
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.allow_notifications)) }
                }
            }
        }
        if (state.batteryOptimizationActive) {
            item { ReminderInformationCard(stringResource(R.string.battery_optimization_explanation)) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.new_task), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTaskTitle,
                        label = { Text(stringResource(R.string.task_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.dueDate,
                        onValueChange = viewModel::updateTaskDueDate,
                        label = { Text(stringResource(R.string.task_due_date_label)) },
                        supportingText = { Text(stringResource(R.string.task_due_date_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.time,
                        onValueChange = viewModel::updateTaskTime,
                        label = { Text(stringResource(R.string.task_time_label)) },
                        supportingText = { Text(stringResource(R.string.task_time_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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
                        Text(stringResource(R.string.task_validation_error), color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = viewModel::createTask,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.save_task)) }
                }
            }
        }
        item { Text(stringResource(R.string.pending_tasks), style = MaterialTheme.typography.titleLarge) }
        if (state.pendingOccurrences.isEmpty()) {
            item { Text(stringResource(R.string.no_pending_tasks)) }
        } else {
            items(state.pendingOccurrences, key = TaskOccurrence::id) { occurrence ->
                TaskOccurrenceCard(occurrence, viewModel)
            }
        }
    }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(occurrence.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.task_due_value,
                    occurrence.dueDate.toIsoText(),
                    occurrence.minuteOfDay?.toTimeText() ?: stringResource(R.string.task_no_time),
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.COMPLETED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.task_complete)) }
                OutlinedButton(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.POSTPONED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.task_postpone)) }
                TextButton(
                    onClick = { viewModel.resolveTask(occurrence, TaskResolution.IGNORED) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.task_ignore)) }
            }
        }
    }
}

@Composable
private fun TaskRecurrence.label(): String = stringResource(
    when (this) {
        TaskRecurrence.ONCE -> R.string.task_once
        TaskRecurrence.DAILY -> R.string.task_daily
        TaskRecurrence.WEEKLY -> R.string.task_weekly
        TaskRecurrence.MONTHLY -> R.string.task_monthly
    },
)

internal fun com.maximerollin.aqualog.shared.TaskDate.toIsoText(): String =
    "%04d-%02d-%02d".format(year, month, day)

private fun Int.toTimeText(): String = "%02d:%02d".format(this / 60, this % 60)
