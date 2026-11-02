package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.shared.isOutsideIndicativeRange
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.ReadingValue
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private sealed interface TimelineListItem {
    val timestamp: Long
    val key: String

    data class SessionItem(val entry: TimelineSession) : TimelineListItem {
        override val timestamp = entry.session.occurredAtEpochMillis
        override val key = "session-${entry.session.id}"
    }

    data class TaskItem(val occurrence: TaskOccurrence) : TimelineListItem {
        override val timestamp = requireNotNull(occurrence.resolvedAtEpochMillis)
        override val key = "task-${occurrence.id}"
    }
}


private val RailWidth = 9.dp
private val RailGap = 10.dp

@Composable
fun TimelineScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val timeline = state.timeline
    val context = listOfNotNull(
        state.aquariums.firstOrNull { it.id == timeline.aquariumId }?.name
            ?: stringResource(Res.string.timeline_all_aquariums),
        stringResource(timeline.period.labelRes()),
        state.setup?.parameters?.firstOrNull { it.id == timeline.parameterDefinitionId }?.parameter?.label(),
        timeline.maintenanceActionType?.label(),
    ).joinToString(" · ")
    Column(modifier.fillMaxSize()) {
        CarnetScreenTitle(
            stringResource(Res.string.timeline_title),
            context,
            Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            action = {
                IconButton(onClick = viewModel::toggleTimelineFilters, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(Res.drawable.ic_filter), contentDescription = stringResource(Res.string.timeline_filters))
                }
            },
        )
        when {
            timeline.isLoading -> TimelineLoading()
            timeline.hasRecoverableError -> TimelineError(viewModel::retryTimeline)
            else -> TimelineList(state, viewModel)
        }
    }
    if (timeline.filtersExpanded) TimelineFiltersSheet(state, viewModel)
}

private fun TimelinePeriod.labelRes() = when (this) {
    TimelinePeriod.ALL -> Res.string.timeline_period_all
    TimelinePeriod.SEVEN_DAYS -> Res.string.timeline_period_7
    TimelinePeriod.THIRTY_DAYS -> Res.string.timeline_period_30
    TimelinePeriod.NINETY_DAYS -> Res.string.timeline_period_90
}

@Composable
private fun TimelineList(state: HomeUiState, viewModel: HomeViewModel) {
    val resolvedTasks = state.timeline.resolvedTaskOccurrences.filter { occurrence ->
        val periodStart = state.timeline.period.days?.let { days ->
            System.currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
        }
        (state.timeline.aquariumId == null || occurrence.aquariumId == state.timeline.aquariumId) &&
            state.timeline.parameterDefinitionId == null &&
            state.timeline.maintenanceActionType == null &&
            (periodStart == null || (occurrence.resolvedAtEpochMillis ?: Long.MIN_VALUE) >= periodStart)
    }
    val items = (
        state.timeline.entries.map(TimelineListItem::SessionItem) +
            resolvedTasks.map(TimelineListItem::TaskItem)
        ).sortedWith(compareByDescending<TimelineListItem> { it.timestamp }.thenBy { it.key })
    val showAquarium = state.timeline.aquariumId == null && state.aquariums.size > 1
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        if (items.isEmpty()) {
            item {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.timeline_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.timeline_empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                val formatter = currentLocalizedFormatters()
                val date = formatter.date(item.timestamp)
                if (index == 0 || formatter.date(items[index - 1].timestamp) != date) {
                    Text(
                        date,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val isLast = index == items.lastIndex ||
                    formatter.date(items[index + 1].timestamp) != date
                when (item) {
                    is TimelineListItem.TaskItem -> TimelineTaskRow(item.occurrence, isLast)
                    is TimelineListItem.SessionItem -> TimelineSessionRow(
                        item.entry,
                        isLast,
                        showAquarium,
                        onClick = { viewModel.openTimelineSession(item.entry.session.id) },
                    )
                }
            }
        }
    }
}

/** One journal entry: a decorative rail (dot + line to the next entry of the day) beside its content. */
@Composable
private fun RailEntry(
    dotColor: Color,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val radius = RailWidth.toPx() / 2
                val centerX = radius
                val centerY = 12.dp.toPx()
                drawCircle(dotColor, radius, Offset(centerX, centerY))
                if (!isLast) {
                    drawLine(
                        lineColor,
                        Offset(centerX, centerY + radius + 3.dp.toPx()),
                        Offset(centerX, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            .padding(start = RailWidth + RailGap, top = 6.dp, bottom = 6.dp),
    ) { content() }
}

@Composable
private fun TimelineTaskRow(occurrence: TaskOccurrence, isLast: Boolean) {
    val formatter = currentLocalizedFormatters()
    val resolutionLabel = when (occurrence.resolution) {
        TaskResolution.COMPLETED -> stringResource(Res.string.task_completed)
        TaskResolution.POSTPONED -> stringResource(Res.string.task_postponed)
        TaskResolution.IGNORED -> stringResource(Res.string.task_ignored)
        null -> return
    }
    val resolution = "$resolutionLabel · ${formatter.clock(requireNotNull(occurrence.resolvedAtEpochMillis))}"
    RailEntry(MaterialTheme.colorScheme.outlineVariant, isLast) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Text(occurrence.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                resolution,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineSessionRow(entry: TimelineSession, isLast: Boolean, showAquarium: Boolean, onClick: () -> Unit) {
    val formatter = currentLocalizedFormatters()
    val description = stringResource(Res.string.open_session_details)
    val attentionCue = stringResource(Res.string.reading_attention_description)
    val readings = entry.measurements.sortedBy { it.definition.position }.map { item ->
        val label = item.definition.parameter.label()
        TimelineReading(
            value = formatter.number(item.measurement.value, item.definition.precision),
            unit = item.definition.unit.takeIf { it.isNotBlank() && it != label },
            label = label,
            attention = item.definition.isOutsideIndicativeRange(item.measurement.value),
        )
    }
    // Each ReadingValue is its own merged node; the row announces them as one unit with its click action.
    val readingsSummary = readings.joinToString(", ") { reading ->
        listOfNotNull(reading.value, reading.unit, reading.label).joinToString(" ") +
            if (reading.attention) ", $attentionCue" else ""
    }
    RailEntry(
        MaterialTheme.colorScheme.primary,
        isLast,
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (readingsSummary.isNotEmpty()) stateDescription = readingsSummary
            }
            .heightIn(min = 48.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.timeline_session),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    listOfNotNull(
                        entry.aquarium.name.takeIf { showAquarium },
                        formatter.clock(entry.session.occurredAtEpochMillis),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (readings.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(readingsSummary) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    readings.forEach { reading ->
                        ReadingValue(
                            value = reading.value,
                            label = reading.label,
                            unit = reading.unit,
                            attention = reading.attention,
                        )
                    }
                }
            }
            entry.maintenanceActions.forEach { action ->
                TimelineNoteLine(Res.drawable.ic_drop, action.summary(formatter))
            }
            entry.events.forEach { event ->
                val note = if (event.type == EventType.INCIDENT) {
                    "${stringResource(Res.string.incident)} · ${event.note}"
                } else {
                    event.note
                }
                TimelineNoteLine(Res.drawable.ic_note, note)
            }
        }
    }
}

private data class TimelineReading(val value: String, val unit: String?, val label: String, val attention: Boolean)

@Composable
private fun MaintenanceAction.summary(formatter: LocalizedFormatters): String {
    val details = listOfNotNull(quantity?.let { formatter.number(it, 2) }, unit, product).joinToString(" ")
    return listOf(type.label(), details).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
private fun TimelineNoteLine(icon: org.jetbrains.compose.resources.DrawableResource, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimelineFiltersSheet(state: HomeUiState, viewModel: HomeViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::toggleTimelineFilters,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CarnetSectionTitle(
                stringResource(Res.string.timeline_filters),
                actionLabel = stringResource(Res.string.close),
                onAction = viewModel::toggleTimelineFilters,
            )
            if (state.timeline.isOffline) {
                Text(
                    stringResource(Res.string.timeline_offline_status),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilterGroup {
                TimelinePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.timeline.period == period,
                        onClick = { viewModel.selectTimelinePeriod(period) },
                        label = { Text(stringResource(period.labelRes())) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            FilterGroup {
                FilterChip(
                    selected = state.timeline.aquariumId == null,
                    onClick = { viewModel.selectTimelineAquarium(null) },
                    label = { Text(stringResource(Res.string.timeline_all_aquariums)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                state.aquariums.forEach { aquarium ->
                    FilterChip(
                        selected = state.timeline.aquariumId == aquarium.id,
                        onClick = { viewModel.selectTimelineAquarium(aquarium.id) },
                        label = { Text(aquarium.name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            FilterGroup {
                FilterChip(
                    selected = state.timeline.parameterDefinitionId == null,
                    onClick = { viewModel.selectTimelineParameter(null) },
                    label = { Text(stringResource(Res.string.timeline_all_parameters)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                state.setup?.parameters?.filter { it.isActive }?.forEach { definition ->
                    FilterChip(
                        selected = state.timeline.parameterDefinitionId == definition.id,
                        onClick = { viewModel.selectTimelineParameter(definition.id) },
                        label = { Text(definition.parameter.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            FilterGroup {
                FilterChip(
                    selected = state.timeline.maintenanceActionType == null,
                    onClick = { viewModel.selectTimelineAction(null) },
                    label = { Text(stringResource(Res.string.timeline_all_actions)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                MaintenanceActionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.timeline.maintenanceActionType == type,
                        onClick = { viewModel.selectTimelineAction(type) },
                        label = { Text(type.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun TimelineLoading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.timeline_loading), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TimelineError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.timeline_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.retry))
        }
    }
}
