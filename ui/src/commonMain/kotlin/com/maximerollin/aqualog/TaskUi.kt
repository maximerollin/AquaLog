package com.maximerollin.aqualog

import com.maximerollin.aqualog.ui.theme.IconTile
import com.maximerollin.aqualog.ui.theme.LargeFontScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.toEpochDay
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.PickerTile
import com.maximerollin.aqualog.ui.theme.SettingsGroup
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import com.maximerollin.aqualog.ui.theme.levelContainerColor
import com.maximerollin.aqualog.ui.theme.overlapNext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.jetbrains.compose.resources.stringResource

/** Tasks and reminders: the Aquarium's water with the title, back and « + », then the pending Occurrences by day. */
@Composable
fun TaskScreen(
    state: TaskUiState,
    viewModel: HomeViewModel,
    aquarium: Aquarium,
    modifier: Modifier = Modifier,
) {
    TaskContent(
        state = state,
        aquarium = aquarium,
        today = viewModel.today(),
        actions = TaskActions(
            onBack = viewModel::closeTasks,
            onComplete = viewModel::completeTask,
            onResolve = viewModel::resolveTask,
            onTitleChange = viewModel::updateTaskTitle,
            onDueDateChange = viewModel::updateTaskDueDate,
            onTimeChange = viewModel::updateTaskTime,
            onRecurrenceSelect = viewModel::selectTaskRecurrence,
            onLinkedActionSelect = viewModel::selectTaskLinkedAction,
            onSave = viewModel::createTask,
        ),
        modifier = modifier,
    )
}

/** What the Tasks screen asks for; [TaskScreen] wires them to the [HomeViewModel], a test to fakes. */
internal class TaskActions(
    val onBack: () -> Unit = {},
    val onComplete: (occurrence: TaskOccurrence, onFailed: () -> Unit) -> Unit = { _, _ -> },
    val onResolve: (TaskOccurrence, TaskResolution) -> Unit = { _, _ -> },
    val onTitleChange: (String) -> Unit = {},
    val onDueDateChange: (String) -> Unit = {},
    val onTimeChange: (String) -> Unit = {},
    val onRecurrenceSelect: (TaskRecurrence) -> Unit = {},
    val onLinkedActionSelect: (type: MaintenanceActionType?, suggestedTitle: String) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
)

/** One day of the Tasks screen: its date and its Occurrences, in the order shown. */
internal data class TaskDayGroup(val date: TaskDate, val occurrences: List<TaskOccurrence>)

/**
 * The pending Occurrences by day, earliest day first. An overdue Occurrence joins [today]'s group; within a group, overdue
 * ones come first, then by due date and time, untimed last; the input order breaks ties.
 */
internal fun taskDayGroups(occurrences: List<TaskOccurrence>, today: TaskDate): List<TaskDayGroup> {
    val todayEpochDay = today.toEpochDay()
    return occurrences.groupBy { maxOf(it.dueDate.toEpochDay(), todayEpochDay) }.entries.sortedBy { it.key }.map { (day, inDay) ->
        TaskDayGroup(
            TaskDate.fromEpochDay(day),
            inDay.sortedWith(compareBy<TaskOccurrence> { it.dueDate.toEpochDay() }.thenBy(nullsLast()) { it.minuteOfDay }),
        )
    }
}

/** The Tasks screen drawn from [state] alone: the single scrollable `task-list`, its water under the status bar. */
@Composable
internal fun TaskContent(
    state: TaskUiState,
    aquarium: Aquarium,
    today: TaskDate,
    actions: TaskActions,
    modifier: Modifier = Modifier,
) {
    val requestNotifications = rememberNotificationPermissionRequest()
    val formatter = currentLocalizedFormatters()
    var creating by rememberSaveable { mutableStateOf(false) }
    var seenSaves by remember { mutableStateOf(state.savedCount) }
    // The sheet closes after a successful save only; an invalid or failed save keeps it open.
    LaunchedEffect(state.savedCount) {
        if (state.savedCount != seenSaves) {
            seenSaves = state.savedCount
            creating = false
        }
    }
    val groups = remember(state.pendingOccurrences, today) { taskDayGroups(state.pendingOccurrences, today) }
    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("task-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = aquarium,
                    title = stringResource(Res.string.tasks_title),
                    subtitle = aquarium.name,
                    leading = {
                        GlassIconButton(
                            AquagendaIcons.ArrowLeft, stringResource(Res.string.back), actions.onBack,
                            Modifier.testTag("tasks-back"),
                        )
                    },
                    action = { GlassIconButton(AquagendaIcons.Plus, stringResource(Res.string.new_task), { creating = true }) },
                    // The sheet's top edge, the next item, covers the water's bottom.
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            // One banner: the permission comes first, the battery note once notifications are allowed.
            if (requestNotifications != null) {
                item(key = "banner") {
                    ReminderBanner(
                        stringResource(Res.string.notification_permission_explanation),
                        stringResource(Res.string.allow_notifications),
                        requestNotifications,
                    )
                }
            } else if (state.batteryOptimizationActive) {
                item(key = "banner") { ReminderBanner(stringResource(Res.string.battery_optimization_explanation)) }
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.no_pending_tasks),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groups.forEach { group ->
                val day = group.date.toIsoText()
                item(key = "label-$day") {
                    GroupLabel(
                        when (group.date) {
                            today -> stringResource(Res.string.home_today)
                            today.plusDays(1) -> stringResource(Res.string.task_day_tomorrow)
                            else -> formatter.date(group.date.year, group.date.month, group.date.day)
                        },
                        Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }
                item(key = "day-$day") {
                    SettingsGroup(Modifier.padding(bottom = 14.dp)) {
                        group.occurrences.forEach { occurrence ->
                            key(occurrence.id) {
                                TaskRow(
                                    occurrence = occurrence,
                                    recurrence = state.recurrenceByTaskId[occurrence.taskId],
                                    overdue = occurrence.dueDate.toEpochDay() < today.toEpochDay(),
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }
        StatusBarOverHeader(listState)
    }
    if (creating) NewTaskSheet(state, actions, onDismiss = { creating = false })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewTaskSheet(state: TaskUiState, actions: TaskActions, onDismiss: () -> Unit) {
    val formatter = currentLocalizedFormatters()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // The title and « Fermer » stay put while the form scrolls under them, so closing is always one tap away.
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.new_task),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = TextStyle(fontFamily = frauncesFamily(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.close))
            }
        }
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val validationError = stringResource(Res.string.task_validation_error)
            FilledField(
                value = state.title,
                onValueChange = actions.onTitleChange,
                label = stringResource(Res.string.task_title_label),
                isError = state.showValidationError && state.title.isBlank(),
                errorMessage = validationError,
            )
            val selectedDate = runCatching { LocalDate.parse(state.dueDate) }
                .getOrElse { LocalDate.now() }
            val selectedTime = runCatching { LocalTime.parse(state.time) }
                .getOrElse { LocalTime.of(9, 0) }
            val dateTile = @Composable { tileModifier: Modifier ->
                PickerTile(
                    label = stringResource(Res.string.task_due_date_label),
                    value = formatter.date(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                    onClick = { pickingDate = true },
                    modifier = tileModifier,
                )
            }
            val timeTile = @Composable { tileModifier: Modifier ->
                PickerTile(
                    label = stringResource(Res.string.task_time_label),
                    value = state.time.toMinuteOfDayOrNull()?.let(formatter::time) ?: stringResource(Res.string.task_no_time),
                    onClick = { pickingTime = true },
                    modifier = tileModifier,
                )
            }
            // Side by side, one height; at large font sizes they stack so neither label is cut.
            if (LocalDensity.current.fontScale >= LargeFontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dateTile(Modifier.fillMaxWidth())
                    timeTile(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    dateTile(Modifier.weight(1f).fillMaxHeight())
                    timeTile(Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (pickingDate) {
                TaskDatePickerDialog(
                    selectedDate = selectedDate,
                    onDateSelected = { actions.onDueDateChange(it.toString()) },
                    onDismiss = { pickingDate = false },
                )
            }
            if (pickingTime) {
                TaskTimePickerDialog(
                    selectedTime = selectedTime,
                    onTimeSelected = { actions.onTimeChange("%02d:%02d".format(it.hour, it.minute)) },
                    onDismiss = { pickingTime = false },
                )
            }
            if (state.time.isNotBlank()) {
                TextButton(
                    onClick = { actions.onTimeChange("") },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.clear_task_time))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskRecurrence.entries.forEach { recurrence ->
                    FilterChip(
                        selected = state.recurrence == recurrence,
                        onClick = { actions.onRecurrenceSelect(recurrence) },
                        label = { Text(recurrence.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(stringResource(Res.string.task_link_action_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.linkedAction == null,
                    onClick = { actions.onLinkedActionSelect(null, "") },
                    label = { Text(stringResource(Res.string.task_link_action_none)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                MaintenanceActionType.entries.forEach { type ->
                    val label = type.label()
                    FilterChip(
                        selected = state.linkedAction == type,
                        onClick = { actions.onLinkedActionSelect(type, label) },
                        label = { Text(label) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            if (state.showValidationError) {
                Text(validationError, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = actions.onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.save_task)) }
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

/** A reminder note on the paper: the attention tint, a bell, the message and its optional action. */
@Composable
private fun ReminderBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val hasAction = actionLabel != null && onAction != null
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth()
            .background(levelContainerColor(ReadingLevel.ATTENTION) ?: colors.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (hasAction) 4.dp else 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(AquagendaIcons.Bell, contentDescription = null, tint = colors.onSurface, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
            }
        }
    }
}

/**
 * One pending Occurrence: a round check, its action's icon, the title over « late · date · recurrence » or « time · recurrence »,
 * then the ⋮ menu. An unknown recurrence is left out; « late » is in the alert colour and in words.
 */
@Composable
private fun TaskRow(occurrence: TaskOccurrence, recurrence: TaskRecurrence?, overdue: Boolean, actions: TaskActions) {
    val colors = MaterialTheme.colorScheme
    val formatter = currentLocalizedFormatters()
    // Guards against a double tap resolving the occurrence twice before the list refreshes.
    var completed by remember(occurrence.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val completeDescription = "${stringResource(Res.string.task_complete)}, ${occurrence.title}"
    val moreDescription = "${stringResource(Res.string.task_more_actions)}, ${occurrence.title}"
    val overdueLabel = stringResource(Res.string.task_overdue)
    val lead = if (overdue) {
        stringResource(
            Res.string.task_row_detail,
            overdueLabel,
            formatter.date(occurrence.dueDate.year, occurrence.dueDate.month, occurrence.dueDate.day),
        )
    } else {
        occurrence.minuteOfDay?.let(formatter::time) ?: stringResource(Res.string.task_no_time)
    }
    val detail = recurrence?.let { stringResource(Res.string.task_row_detail, lead, it.label()) } ?: lead
    val styledDetail = buildAnnotatedString {
        append(detail)
        val start = if (overdue) detail.indexOf(overdueLabel) else -1
        if (start >= 0) {
            addStyle(SpanStyle(color = colors.error, fontWeight = FontWeight.SemiBold), start, start + overdueLabel.length)
        }
    }
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp)
                .toggleable(value = completed, enabled = !completed, role = Role.Checkbox) {
                    completed = true
                    actions.onComplete(occurrence) { completed = false }
                }
                .semantics { contentDescription = completeDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).background(if (completed) colors.primary else Color.Transparent, CircleShape)
                    .border(2.dp, colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (completed) Icon(AquagendaIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        IconTile(occurrence.maintenanceActionType?.icon() ?: AquagendaIcons.Bell, Modifier.padding(start = 4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // The icon tile already shows the linked action: the title does not repeat it.
            LinkedTaskTitle(occurrence.title, occurrence.maintenanceActionType, showIcon = false)
            Text(styledDetail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                Icon(AquagendaIcons.EllipsisVertical, contentDescription = moreDescription)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.task_postpone)) },
                    onClick = {
                        menuOpen = false
                        actions.onResolve(occurrence, TaskResolution.POSTPONED)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.task_ignore)) },
                    onClick = {
                        menuOpen = false
                        actions.onResolve(occurrence, TaskResolution.IGNORED)
                    },
                )
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

internal fun TaskDate.toIsoText(): String =
    "%04d-%02d-%02d".format(year, month, day)

private fun String.toMinuteOfDayOrNull(): Int? {
    val parts = split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
