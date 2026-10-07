package com.maximerollin.aqualog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.SessionEvent
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val selected = state.timeline.selectedSessionId?.let { selectedId ->
        state.timeline.entries.firstOrNull { it.session.id == selectedId }
    }
    if (selected != null) {
        TimelineDetail(selected, viewModel, modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.historySection == HistorySection.CHRONOLOGY,
                onClick = { viewModel.selectHistorySection(HistorySection.CHRONOLOGY) },
                label = { Text(stringResource(R.string.timeline_title)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = state.historySection == HistorySection.TRENDS,
                onClick = { viewModel.selectHistorySection(HistorySection.TRENDS) },
                label = { Text(stringResource(R.string.trends_title)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (state.historySection == HistorySection.TRENDS) {
            TrendsScreen(state, viewModel, Modifier.weight(1f))
        } else {
            TimelineContent(state, viewModel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimelineContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.timeline.isOffline) {
                Text(
                    stringResource(R.string.timeline_offline_status),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when {
            state.timeline.isLoading -> TimelineLoading()
            state.timeline.hasRecoverableError -> TimelineError(viewModel::retryTimeline)
            else -> TimelineList(state, viewModel)
        }
    }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TimelineFilters(state, viewModel) }
        if (state.timeline.entries.isEmpty() && resolvedTasks.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.timeline_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.timeline_empty_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(resolvedTasks, key = { "task-${it.id}" }) { occurrence ->
                TimelineTaskCard(occurrence)
            }
            items(state.timeline.entries, key = { it.session.id }) { entry ->
                TimelineSessionCard(entry, onClick = { viewModel.openTimelineSession(entry.session.id) })
            }
        }
    }
}

@Composable
private fun TimelineTaskCard(occurrence: TaskOccurrence) {
    val resolutionLabel = when (occurrence.resolution) {
        TaskResolution.COMPLETED -> stringResource(R.string.task_completed)
        TaskResolution.POSTPONED -> stringResource(R.string.task_postponed)
        TaskResolution.IGNORED -> stringResource(R.string.task_ignored)
        null -> return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.timeline_task), style = MaterialTheme.typography.titleMedium)
            Text(occurrence.title)
            Text(
                stringResource(
                    R.string.task_resolved_value,
                    resolutionLabel,
                    DateFormat.getDateTimeInstance().format(Date(requireNotNull(occurrence.resolvedAtEpochMillis))),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineFilters(state: HomeUiState, viewModel: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterRow {
            TimelinePeriod.entries.forEach { period ->
                val label = when (period) {
                    TimelinePeriod.ALL -> stringResource(R.string.timeline_period_all)
                    TimelinePeriod.SEVEN_DAYS -> stringResource(R.string.timeline_period_7)
                    TimelinePeriod.THIRTY_DAYS -> stringResource(R.string.timeline_period_30)
                    TimelinePeriod.NINETY_DAYS -> stringResource(R.string.timeline_period_90)
                }
                FilterChip(
                    selected = state.timeline.period == period,
                    onClick = { viewModel.selectTimelinePeriod(period) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        FilterRow {
            FilterChip(
                selected = state.timeline.aquariumId == null,
                onClick = { viewModel.selectTimelineAquarium(null) },
                label = { Text(stringResource(R.string.timeline_all_aquariums)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            state.setup?.aquarium?.let { aquarium ->
                FilterChip(
                    selected = state.timeline.aquariumId == aquarium.id,
                    onClick = { viewModel.selectTimelineAquarium(aquarium.id) },
                    label = { Text(aquarium.name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        FilterRow {
            FilterChip(
                selected = state.timeline.parameterDefinitionId == null,
                onClick = { viewModel.selectTimelineParameter(null) },
                label = { Text(stringResource(R.string.timeline_all_parameters)) },
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
        FilterRow {
            FilterChip(
                selected = state.timeline.maintenanceActionType == null,
                onClick = { viewModel.selectTimelineAction(null) },
                label = { Text(stringResource(R.string.timeline_all_actions)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            MaintenanceActionType.entries.forEach { type ->
                FilterChip(
                    selected = state.timeline.maintenanceActionType == type,
                    onClick = { viewModel.selectTimelineAction(type) },
                    label = { Text(type.timelineLabel()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun TimelineSessionCard(entry: TimelineSession, onClick: () -> Unit) {
    val description = stringResource(R.string.open_session_details)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.timeline_session), style = MaterialTheme.typography.titleMedium)
            Text(entry.effectiveDate(), style = MaterialTheme.typography.bodyMedium)
            Text(entry.aquarium.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                sessionSummary(entry),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TimelineDetail(entry: TimelineSession, viewModel: HomeViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = viewModel::closeTimelineSession, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.back_to_timeline))
            }
        }
        item {
            Text(stringResource(R.string.session_details), style = MaterialTheme.typography.headlineMedium)
            Text(entry.effectiveDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.aquarium.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.measurements.isNotEmpty()) {
            item { Text(stringResource(R.string.measurements_heading), style = MaterialTheme.typography.titleLarge) }
            items(entry.measurements, key = { it.measurement.id }) { item ->
                DetailCard(
                    "${item.definition.parameter.label()} · " +
                        "${item.measurement.value.displayValue(item.definition.precision)} ${item.definition.unit}",
                )
            }
        }
        if (entry.maintenanceActions.isNotEmpty()) {
            item { Text(stringResource(R.string.maintenance_heading), style = MaterialTheme.typography.titleLarge) }
            items(entry.maintenanceActions, key = MaintenanceAction::id) { action ->
                val details = listOfNotNull(
                    action.quantity?.displayValue(2),
                    action.unit,
                    action.product,
                ).joinToString(" ")
                DetailCard(listOf(action.type.timelineLabel(), details).filter(String::isNotBlank).joinToString(" · "))
            }
        }
        if (entry.events.isNotEmpty()) {
            item { Text(stringResource(R.string.timeline_events), style = MaterialTheme.typography.titleLarge) }
            items(entry.events, key = SessionEvent::id) { event ->
                val type = when (event.type) {
                    EventType.OBSERVATION -> stringResource(R.string.observation)
                    EventType.INCIDENT -> stringResource(R.string.incident)
                }
                DetailCard("$type · ${event.note}")
            }
        }
    }
}

@Composable
private fun DetailCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TimelineLoading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.timeline_loading), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TimelineError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.timeline_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun sessionSummary(entry: TimelineSession): String = listOf(
    pluralStringResource(R.plurals.timeline_measurement_count, entry.measurements.size, entry.measurements.size),
    pluralStringResource(
        R.plurals.timeline_action_count,
        entry.maintenanceActions.size,
        entry.maintenanceActions.size,
    ),
    pluralStringResource(R.plurals.timeline_event_count, entry.events.size, entry.events.size),
).joinToString(" · ")

@Composable
private fun MaintenanceActionType.timelineLabel(): String = stringResource(
    when (this) {
        MaintenanceActionType.WATER_CHANGE -> R.string.action_water_change
        MaintenanceActionType.FERTILIZATION -> R.string.action_fertilization
        MaintenanceActionType.FILTER_MAINTENANCE -> R.string.action_filter
        MaintenanceActionType.VACUUM -> R.string.action_vacuum
        MaintenanceActionType.GLASS_CLEANING -> R.string.action_glass
        MaintenanceActionType.PRUNING -> R.string.action_pruning
    },
)

private fun TimelineSession.effectiveDate(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.occurredAtEpochMillis))

private fun Double.displayValue(maximumPrecision: Int): String =
    String.format(Locale.US, "%.${maximumPrecision}f", this).trimEnd('0').trimEnd('.')
