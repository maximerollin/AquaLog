package com.maximerollin.aqualog

import com.maximerollin.aqualog.ui.icon.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceAction
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TimelineSession
import com.maximerollin.aqualog.encyclopedia.displayName
import com.maximerollin.aqualog.encyclopedia.loadEncyclopedia
import com.maximerollin.aqualog.shared.TimelineInhabitantChange
import com.maximerollin.aqualog.shared.encyclopedia.Species
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.FloatingNavBarClearance
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.PeriodSegmented
import com.maximerollin.aqualog.ui.theme.TimelineNode
import com.maximerollin.aqualog.ui.theme.ValuePill
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
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

    data class InhabitantItem(val change: TimelineInhabitantChange) : TimelineListItem {
        override val timestamp = change.atEpochMillis
        override val key = "inhabitant-${if (change.removed) "removed" else "added"}-${change.inhabitant.id}"
    }
}

/** Size of a [TimelineNode]: the line joining a day's nodes runs through its centre. */
private val NodeSize = 36.dp

/**
 * The History screen: the setup Aquarium's water with the filter context, then the paper sheet with the period control and
 * the entries grouped by day, each on a node of a vertical line. It draws under the status bar and clears the floating bar.
 */
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
        state.setup?.parameters?.firstOrNull { it.id == timeline.parameterDefinitionId }?.parameter?.label(),
        timeline.maintenanceActionType?.label(),
    ).joinToString(" · ")
    val title = stringResource(Res.string.timeline_title)
    val filtersLabel = stringResource(Res.string.timeline_filters)
    val sceneAquarium = state.setup?.aquarium ?: state.aquariums.firstOrNull()
    val periodLabels = TimelinePeriod.entries.map { stringResource(it.labelRes()) }
    val listState = rememberLazyListState()
    val encyclopedia by produceState<List<Species>?>(null) {
        // Unavailable file: lines fall back to the species id.
        value = try { loadEncyclopedia() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    }
    val language = remember { Locale.getDefault().language }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("timeline-list"),
            contentPadding = PaddingValues(bottom = FloatingNavBarClearance.dp),
        ) {
            item(key = "header") {
                if (sceneAquarium != null) {
                    WaterHeader(
                        aquarium = sceneAquarium,
                        title = title,
                        subtitle = context,
                        action = { GlassIconButton(AquagendaIcons.Funnel, filtersLabel, viewModel::toggleTimelineFilters) },
                        // The sheet's top edge, the next item, covers the water's bottom.
                        modifier = Modifier.overlapNext(30.dp),
                    )
                } else {
                    // shortcut: no Aquarium to draw the water from; the shell only opens with a setup, so this is never seen.
                    Column {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        CarnetScreenTitle(
                            title, context, Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                            action = { GlassIconButton(AquagendaIcons.Funnel, filtersLabel, viewModel::toggleTimelineFilters) },
                        )
                    }
                }
            }
            item(key = "sheet-top") { PaperSheetTop() }
            paperItem("period") {
                PeriodSegmented(
                    labels = periodLabels,
                    selectedIndex = TimelinePeriod.entries.indexOf(timeline.period),
                    onSelect = { viewModel.selectTimelinePeriod(TimelinePeriod.entries[it]) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                timeline.isLoading -> paperItem("loading") { TimelineLoading() }
                timeline.hasRecoverableError -> paperItem("error") { TimelineError(viewModel::retryTimeline) }
                else -> timelineEntries(
                    items = timelineItems(state),
                    showAquarium = timeline.aquariumId == null && state.aquariums.size > 1,
                    inhabitantName = { it.inhabitant.displayName(encyclopedia, language) },
                    onOpenSession = viewModel::openTimelineSession,
                )
            }
        }
        StatusBarOverHeader(listState)
    }
    if (timeline.filtersExpanded) TimelineFiltersSheet(state, viewModel)
}

private fun TimelinePeriod.labelRes() = when (this) {
    TimelinePeriod.ALL -> Res.string.timeline_period_all
    TimelinePeriod.SEVEN_DAYS -> Res.string.timeline_period_7
    TimelinePeriod.THIRTY_DAYS -> Res.string.timeline_period_30
    TimelinePeriod.NINETY_DAYS -> Res.string.timeline_period_90
}

/** A sheet item: paper behind it edge to edge, its content 16 dp from the sides. */
private fun LazyListScope.paperItem(key: String, content: @Composable () -> Unit) = item(key = key) { OnPaper(content) }

@Composable
private fun OnPaper(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) { content() }
}

/** Sessions, resolved tasks and inhabitant changes that match the filters, latest first. */
private fun timelineItems(state: HomeUiState): List<TimelineListItem> {
    val resolvedTasks = state.timeline.resolvedTaskOccurrences.filter { occurrence ->
        val periodStart = state.timeline.period.days?.let { days ->
            System.currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
        }
        (state.timeline.aquariumId == null || occurrence.aquariumId == state.timeline.aquariumId) &&
            state.timeline.parameterDefinitionId == null &&
            state.timeline.maintenanceActionType == null &&
            (periodStart == null || (occurrence.resolvedAtEpochMillis ?: Long.MIN_VALUE) >= periodStart)
    }
    // Like Tâches, inhabitants belong to no Parameter or maintenance Action: those filters hide them.
    val inhabitantChanges = state.timeline.inhabitantChanges.filter {
        state.timeline.parameterDefinitionId == null && state.timeline.maintenanceActionType == null
    }
    return (
        state.timeline.entries.map(TimelineListItem::SessionItem) +
            resolvedTasks.map(TimelineListItem::TaskItem) +
            inhabitantChanges.map(TimelineListItem::InhabitantItem)
        ).sortedWith(compareByDescending<TimelineListItem> { it.timestamp }.thenBy { it.key })
}

private fun LazyListScope.timelineEntries(
    items: List<TimelineListItem>,
    showAquarium: Boolean,
    inhabitantName: (TimelineInhabitantChange) -> String,
    onOpenSession: (String) -> Unit,
) {
    if (items.isEmpty()) {
        paperItem("empty") {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.timeline_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.timeline_empty_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
        OnPaper {
            Column {
                val formatter = currentLocalizedFormatters()
                val date = formatter.date(item.timestamp)
                val isFirst = index == 0 || formatter.date(items[index - 1].timestamp) != date
                if (isFirst) {
                    Text(
                        date,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val isLast = index == items.lastIndex || formatter.date(items[index + 1].timestamp) != date
                when (item) {
                    is TimelineListItem.SessionItem -> TimelineEntry(AquagendaIcons.FlaskConical, emphasized = true, isFirst, isLast) {
                        TimelineSessionCard(
                            item.entry,
                            showAquarium,
                            onClick = { onOpenSession(item.entry.session.id) },
                        )
                    }
                    is TimelineListItem.InhabitantItem -> TimelineEntry(AquagendaIcons.Fish, emphasized = false, isFirst, isLast) {
                        TimelineInhabitantLine(item.change, inhabitantName(item.change), showAquarium)
                    }
                    is TimelineListItem.TaskItem -> TimelineEntry(AquagendaIcons.Check, emphasized = false, isFirst, isLast) {
                        TimelineTaskLine(item.occurrence)
                    }
                }
            }
        }
    }
}

/**
 * One entry: its [TimelineNode] on the left, then its content. A 2 dp line runs through the node's centre to the previous
 * and the next entry of the same day; the opaque node is drawn over it.
 */
@Composable
private fun TimelineEntry(
    icon: ImageVector,
    emphasized: Boolean,
    isFirstOfDay: Boolean,
    isLastOfDay: Boolean,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val centerX = NodeSize.toPx() / 2
                val centerY = NodeSize.toPx() / 2
                val stroke = 2.dp.toPx()
                if (!isFirstOfDay) drawLine(lineColor, Offset(centerX, 0f), Offset(centerX, centerY), stroke)
                if (!isLastOfDay) drawLine(lineColor, Offset(centerX, centerY), Offset(centerX, size.height), stroke)
            }
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimelineNode(icon, emphasized)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TimelineTaskLine(occurrence: TaskOccurrence) {
    val formatter = currentLocalizedFormatters()
    val resolutionLabel = when (occurrence.resolution) {
        TaskResolution.COMPLETED -> stringResource(Res.string.task_completed)
        TaskResolution.POSTPONED -> stringResource(Res.string.task_postponed)
        TaskResolution.IGNORED -> stringResource(Res.string.task_ignored)
        null -> return
    }
    val resolution = "$resolutionLabel · ${formatter.clock(requireNotNull(occurrence.resolvedAtEpochMillis))}"
    TimelineLine(occurrence.title, resolution)
}

@Composable
private fun TimelineInhabitantLine(change: TimelineInhabitantChange, name: String, showAquarium: Boolean) {
    val formatter = currentLocalizedFormatters()
    val count = change.inhabitant.count
    val line = if (change.removed) {
        stringResource(Res.string.timeline_inhabitant_removed, name, count)
    } else {
        stringResource(Res.string.timeline_inhabitant_added, name, count)
    }
    TimelineLine(
        line,
        listOfNotNull(change.aquarium.name.takeIf { showAquarium }, formatter.clock(change.atEpochMillis)).joinToString(" · "),
    )
}

/** A plain entry: its line and, at the right, its time; the top padding centres the first line on the node. */
@Composable
private fun TimelineLine(text: String, trailing: String) {
    Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A Session of the History: title and time, its readings as [ValuePill]s, its maintenance actions as chips, its notes.
 * One button: it opens the Session and speaks its readings as its state; the pills are silent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimelineSessionCard(entry: TimelineSession, showAquarium: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val formatter = currentLocalizedFormatters()
    val description = stringResource(Res.string.open_session_details)
    val alertCue = stringResource(Res.string.reading_alert_description)
    val attentionCue = stringResource(Res.string.reading_attention_description)
    val readings = entry.measurements.sortedBy { it.definition.position }.map { item ->
        val label = item.definition.parameter.label()
        val value = item.measurement.value
        TimelineReading(
            value = formatter.number(value, item.definition.precision),
            unit = item.definition.unit.takeIf { it.isNotBlank() && it != label },
            label = label,
            level = readingLevel(item.definition, value, entry.aquarium.profile),
            state = valueRangeState(value, item.definition, entry.aquarium.profile),
        )
    }
    val readingsSummary = readings.joinToString(", ") { reading ->
        listOfNotNull(reading.value, reading.unit, reading.label).joinToString(" ") +
            when (reading.level) {
                ReadingLevel.OK -> ""
                ReadingLevel.ATTENTION -> ", $attentionCue"
                ReadingLevel.ALERT -> ", $alertCue"
            }
    }
    val shape = RoundedCornerShape(20.dp)
    val shadowColor = Color(0xFF1E3733).copy(alpha = .12f)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(colors.surface, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (readingsSummary.isNotEmpty()) stateDescription = readingsSummary
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(stringResource(Res.string.timeline_session), entry.aquarium.name.takeIf { showAquarium })
                    .joinToString(" · "),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                formatter.clock(entry.session.occurredAtEpochMillis),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (readings.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                readings.forEach { reading -> ValuePill(reading.label, reading.value, reading.unit, reading.state) }
            }
        }
        if (entry.maintenanceActions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.maintenanceActions.forEach { action -> ActionChip(action.type, action.summary(formatter)) }
            }
        }
        entry.events.forEach { event ->
            val note = if (event.type == EventType.INCIDENT) {
                "${stringResource(Res.string.incident)} · ${event.note}"
            } else {
                event.note
            }
            TimelineNoteLine(AquagendaIcons.NotepadText, note)
        }
    }
}

/** A maintenance action done in the Session: its icon and summary in a small tinted chip. Not interactive. */
@Composable
private fun ActionChip(type: MaintenanceActionType, summary: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 28.dp).background(colors.secondaryContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(type.icon(), contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSecondaryContainer)
        Text(summary, fontSize = 12.sp, color = colors.onSecondaryContainer)
    }
}

private data class TimelineReading(
    val value: String,
    val unit: String?,
    val label: String,
    val level: ReadingLevel,
    val state: SessionTileState,
)

@Composable
private fun MaintenanceAction.summary(formatter: LocalizedFormatters): String {
    val details = listOfNotNull(quantity?.let { formatter.number(it, 2) }, unit, product).joinToString(" ")
    return listOf(type.label(), details).filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun TimelineNoteLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TimelineFiltersSheet(state: HomeUiState, viewModel: HomeViewModel) {
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
            // The period is chosen on the screen itself (its segmented control), not here.
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
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.timeline_loading), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TimelineError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.timeline_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.retry))
        }
    }
}
