package com.maximerollin.aqualog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.EventType
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendEventMarker
import com.maximerollin.aqualog.shared.TrendPeriod
import com.maximerollin.aqualog.shared.TrendSnapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TrendsScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    when {
        state.trends.isLoading -> TrendLoading(modifier)
        state.trends.hasRecoverableError -> TrendError(viewModel::retryTrends, modifier)
        else -> TrendContent(state, viewModel, modifier)
    }
}

@Composable
private fun TrendContent(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier) {
    val snapshot = state.trends.snapshot
    val selected = snapshot?.series?.firstOrNull {
        it.definition.id == state.trends.selectedParameterDefinitionId
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("trend-list"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trends_heading), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.trends_free_limit),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { TrendPeriods(state.trends.period, viewModel::selectTrendPeriod) }
        if (snapshot != null) {
            item {
                TrendParameters(
                    snapshot = snapshot,
                    selectedParameterDefinitionId = state.trends.selectedParameterDefinitionId,
                    onSelect = viewModel::selectTrendParameter,
                )
            }
        }
        if (selected != null) {
            item { TrendSeriesHeading(selected) }
            item { TrendChart(selected, snapshot) }
            item {
                Text(
                    stringResource(R.string.trends_legend),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Text(stringResource(R.string.trends_exact_values), style = MaterialTheme.typography.titleLarge) }
            if (selected.points.isEmpty()) {
                item { EmptyTrendCard() }
            } else {
                items(selected.points, key = { "trend-point-${it.sessionId}" }) { point ->
                    val value = point.value.displayTrendValue(selected.definition.precision)
                    val exactValueDescription = stringResource(
                        R.string.trends_exact_value_description,
                        value,
                        selected.definition.unit,
                    )
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = exactValueDescription
                            },
                    ) {
                        Text(
                            "${point.occurredAtEpochMillis.displayTrendDate()} · " +
                                "$value ${selected.definition.unit}",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            item { Text(stringResource(R.string.trends_events), style = MaterialTheme.typography.titleLarge) }
            if (snapshot.events.isEmpty()) {
                item { Text(stringResource(R.string.trends_no_events)) }
            } else {
                items(snapshot.events, key = { "trend-event-${it.sessionId}" }) { marker ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "${marker.occurredAtEpochMillis.displayTrendDate()} · ${marker.displayText()}",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendPeriods(selected: TrendPeriod, onSelect: (TrendPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrendPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = {
                    Text(
                        stringResource(
                            when (period) {
                                TrendPeriod.SEVEN_DAYS -> R.string.timeline_period_7
                                TrendPeriod.THIRTY_DAYS -> R.string.timeline_period_30
                                TrendPeriod.NINETY_DAYS -> R.string.timeline_period_90
                            },
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun TrendParameters(
    snapshot: TrendSnapshot,
    selectedParameterDefinitionId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        snapshot.series.forEach { series ->
            FilterChip(
                selected = selectedParameterDefinitionId == series.definition.id,
                onClick = { onSelect(series.definition.id) },
                label = { Text(series.definition.parameter.label()) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun TrendSeriesHeading(series: ParameterTrendSeries) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.trends_parameter_heading, series.definition.parameter.label()),
            style = MaterialTheme.typography.titleLarge,
        )
        val minimum = series.definition.indicativeMinimum
        val maximum = series.definition.indicativeMaximum
        if (minimum != null && maximum != null) {
            Text(
                stringResource(
                    R.string.trends_indicative_target,
                    minimum.displayTrendValue(series.definition.precision),
                    maximum.displayTrendValue(series.definition.precision),
                    series.definition.unit,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendChart(series: ParameterTrendSeries, snapshot: TrendSnapshot) {
    val lineColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.secondary
    val eventColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val description = stringResource(
        R.string.trends_chart_description,
        series.definition.parameter.label(),
    )
    Card(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .semantics { contentDescription = description },
            ) {
                val plotLeft = 12.dp.toPx()
                val plotTop = 12.dp.toPx()
                val plotRight = size.width - 12.dp.toPx()
                val plotBottom = size.height - 12.dp.toPx()
                drawLine(axisColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 1.dp.toPx())
                drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1.dp.toPx())

                val rangeValues = buildList {
                    addAll(series.points.map { it.value })
                    series.definition.indicativeMinimum?.let(::add)
                    series.definition.indicativeMaximum?.let(::add)
                }
                val rawMinimum = rangeValues.minOrNull() ?: 0.0
                val rawMaximum = rangeValues.maxOrNull() ?: 1.0
                val padding = max((rawMaximum - rawMinimum) * 0.12, 0.5)
                val minimum = rawMinimum - padding
                val maximum = rawMaximum + padding
                val yFor: (Double) -> Float = { value ->
                    plotBottom - (((value - minimum) / (maximum - minimum)).toFloat() * (plotBottom - plotTop))
                }
                val windowLength = max(1L, snapshot.windowEndEpochMillis - snapshot.windowStartEpochMillis)
                val xFor: (Long) -> Float = { timestamp ->
                    plotLeft + ((timestamp - snapshot.windowStartEpochMillis).toFloat() / windowLength) *
                        (plotRight - plotLeft)
                }

                val targetMinimum = series.definition.indicativeMinimum
                val targetMaximum = series.definition.indicativeMaximum
                if (targetMinimum != null && targetMaximum != null) {
                    val top = yFor(targetMaximum)
                    val bottom = yFor(targetMinimum)
                    if (top == bottom) {
                        drawLine(
                            color = targetColor,
                            start = Offset(plotLeft, top),
                            end = Offset(plotRight, top),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                        )
                    } else {
                        drawRect(
                            color = targetColor.copy(alpha = 0.12f),
                            topLeft = Offset(plotLeft, top),
                            size = Size(plotRight - plotLeft, bottom - top),
                        )
                        clipRect(plotLeft, top, plotRight, bottom) {
                            var x = plotLeft - size.height
                            while (x < plotRight) {
                                drawLine(
                                    color = targetColor.copy(alpha = 0.55f),
                                    start = Offset(x, bottom),
                                    end = Offset(x + (bottom - top), top),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                x += 12.dp.toPx()
                            }
                        }
                    }
                }

                val line = Path()
                series.points.forEachIndexed { index, point ->
                    val position = Offset(xFor(point.occurredAtEpochMillis), yFor(point.value))
                    if (index == 0) line.moveTo(position.x, position.y) else line.lineTo(position.x, position.y)
                }
                if (series.points.size > 1) drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx()))
                series.points.forEach { point ->
                    drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xFor(point.occurredAtEpochMillis), yFor(point.value)))
                }

                snapshot.events.forEach { event ->
                    val x = xFor(event.occurredAtEpochMillis)
                    drawLine(
                        color = eventColor,
                        start = Offset(x, plotTop),
                        end = Offset(x, plotBottom),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
                    )
                    val radius = 6.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(x, plotTop)
                        lineTo(x + radius, plotTop + radius)
                        lineTo(x, plotTop + radius * 2)
                        lineTo(x - radius, plotTop + radius)
                        close()
                    }
                    drawPath(diamond, eventColor, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun EmptyTrendCard() {
    Card(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.trends_no_measurements), modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun TrendLoading(modifier: Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.trends_loading), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TrendError(onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.trends_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun TrendEventMarker.displayText(): String {
    val actions = maintenanceActionTypes.map { it.trendLabel() }
    val eventLabels = events.map { event ->
        val type = when (event.type) {
            EventType.OBSERVATION -> stringResource(R.string.observation)
            EventType.INCIDENT -> stringResource(R.string.incident)
        }
        "$type: ${event.note}"
    }
    return (actions + eventLabels).joinToString(" · ")
}

@Composable
private fun MaintenanceActionType.trendLabel(): String = stringResource(
    when (this) {
        MaintenanceActionType.WATER_CHANGE -> R.string.action_water_change
        MaintenanceActionType.FERTILIZATION -> R.string.action_fertilization
        MaintenanceActionType.FILTER_MAINTENANCE -> R.string.action_filter
        MaintenanceActionType.VACUUM -> R.string.action_vacuum
        MaintenanceActionType.GLASS_CLEANING -> R.string.action_glass
        MaintenanceActionType.PRUNING -> R.string.action_pruning
    },
)

private fun Long.displayTrendDate(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

private fun Double.displayTrendValue(precision: Int): String =
    String.format(Locale.US, "%.${precision}f", this).trimEnd('0').trimEnd('.')
