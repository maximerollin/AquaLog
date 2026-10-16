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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import com.maximerollin.aqualog.shared.AdvancedTrendAnalysisResult
import com.maximerollin.aqualog.shared.ComparableTrendGroup
import com.maximerollin.aqualog.shared.InterventionSeriesComparison
import com.maximerollin.aqualog.shared.DescriptiveTrendDirection
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
                    stringResource(
                        if (state.trends.advancedAnalysis == null) R.string.trends_free_limit
                        else R.string.trends_pro_complete_history,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.pro.purchaseStatus != ProPurchaseStatus.ACTIVE) {
                    TextButton(
                        onClick = viewModel::openProPaywall,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.unlock_history_beyond_90_days))
                    }
                }
                if (state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE) {
                    Button(
                        onClick = viewModel::showCompleteProHistory,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.trends_complete_history)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.trends.customStartDate,
                            onValueChange = viewModel::updateTrendCustomStart,
                            label = { Text(stringResource(R.string.trends_start_date)) },
                            supportingText = { Text(stringResource(R.string.date_format_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.trends.customEndDate,
                            onValueChange = viewModel::updateTrendCustomEnd,
                            label = { Text(stringResource(R.string.trends_end_date)) },
                            supportingText = { Text(stringResource(R.string.date_format_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = viewModel::applyTrendCustomPeriod,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.trends_apply_custom_period)) }
                    if (state.trends.customDateError) {
                        Text(stringResource(R.string.trends_invalid_custom_period), color = MaterialTheme.colorScheme.error)
                    }
                }
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
            state.trends.advancedAnalysis?.let { analysis ->
                item { AdvancedAnalysis(analysis, viewModel::selectTrendIntervention) }
            }
        }
    }
}

@Composable
private fun AdvancedAnalysis(
    analysis: AdvancedTrendAnalysisResult,
    onSelectIntervention: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.trends_advanced_analysis), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.trends_descriptive_disclaimer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        analysis.comparisonGroups.forEach { group ->
            ComparisonChart(
                group,
                analysis.snapshot.events,
                analysis.snapshot.windowStartEpochMillis,
                analysis.snapshot.windowEndEpochMillis,
            )
        }
        if (analysis.snapshot.events.isNotEmpty()) {
            Text(stringResource(R.string.trends_choose_intervention), style = MaterialTheme.typography.titleMedium)
            analysis.snapshot.events.forEach { marker ->
                TextButton(
                    onClick = { onSelectIntervention(marker.sessionId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("trend-intervention-${marker.sessionId}"),
                ) {
                    Text("${marker.occurredAtEpochMillis.displayTrendDate()} · ${marker.displayText()}")
                }
            }
        }
        analysis.interventionComparison?.let { comparison ->
            Text(stringResource(R.string.trends_before_after), style = MaterialTheme.typography.titleLarge)
            comparison.series.forEach { result ->
                Card(Modifier.fillMaxWidth()) {
                    val parameterName = analysis.snapshot.series.first {
                        it.definition.id == result.definitionId
                    }.definition.parameter.label()
                    val text = when (result) {
                        is InterventionSeriesComparison.Available -> stringResource(
                            R.string.trends_before_after_values,
                            parameterName,
                            result.beforeAverage.displayTrendValue(2),
                            result.afterAverage.displayTrendValue(2),
                            result.unit,
                        )
                        is InterventionSeriesComparison.InsufficientData -> stringResource(
                            R.string.trends_before_after_insufficient,
                            parameterName,
                        )
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text)
                        if (result is InterventionSeriesComparison.Available) {
                            BeforeAfterChart(parameterName, result)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.trends_text_alternative), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                R.string.trends_period_description,
                analysis.snapshot.windowStartEpochMillis.displayTrendDate(),
                analysis.snapshot.windowEndEpochMillis.displayTrendDate(),
            ),
        )
        analysis.textAlternative.series.forEach { alternative ->
            val parameterName = analysis.snapshot.series.first {
                it.definition.id == alternative.definitionId
            }.definition.parameter.label()
            val values = if (alternative.pointCount == 0) {
                stringResource(R.string.trends_text_series_no_data, parameterName, alternative.unit)
            } else {
                stringResource(
                    R.string.trends_text_series_values,
                    parameterName,
                    alternative.pointCount,
                    alternative.unit,
                    alternative.firstValue!!.displayTrendValue(2),
                    alternative.lastValue!!.displayTrendValue(2),
                    alternative.minimumValue!!.displayTrendValue(2),
                    alternative.maximumValue!!.displayTrendValue(2),
                    alternative.direction.label(),
                )
            }
            Text(values)
        }
        analysis.textAlternative.annotations.forEach { annotation ->
            val marker = TrendEventMarker(
                sessionId = "",
                occurredAtEpochMillis = annotation.occurredAtEpochMillis,
                maintenanceActionTypes = annotation.maintenanceActionTypes,
                events = annotation.events,
            )
            Text(
                stringResource(
                    R.string.trends_text_annotation,
                    annotation.occurredAtEpochMillis.displayTrendDate(),
                    marker.displayText(),
                ),
            )
        }
        analysis.textAlternative.interventionComparison?.series?.forEach { comparison ->
            val parameterName = analysis.snapshot.series.first {
                it.definition.id == comparison.definitionId
            }.definition.parameter.label()
            Text(
                when (comparison) {
                    is InterventionSeriesComparison.Available -> stringResource(
                        R.string.trends_before_after_values,
                        parameterName,
                        comparison.beforeAverage.displayTrendValue(2),
                        comparison.afterAverage.displayTrendValue(2),
                        comparison.unit,
                    )
                    is InterventionSeriesComparison.InsufficientData -> stringResource(
                        R.string.trends_before_after_insufficient,
                        parameterName,
                    )
                },
            )
        }
    }
}

@Composable
private fun ComparisonChart(
    group: ComparableTrendGroup,
    annotations: List<TrendEventMarker>,
    start: Long,
    end: Long,
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )
    val eventColor = MaterialTheme.colorScheme.outline
    val seriesLabels = mutableListOf<String>()
    for (series in group.series) seriesLabels += series.definition.parameter.label()
    val description = stringResource(
        R.string.trends_comparison_chart_description,
        seriesLabels.joinToString(),
        group.canonicalUnit,
        annotations.size,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.trends_compatible_comparison, group.canonicalUnit))
            Canvas(
                Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description },
            ) {
                val values = group.series.flatMap(ParameterTrendSeries::points).map { it.value }
                val minimum = values.minOrNull() ?: 0.0
                val maximum = values.maxOrNull() ?: 1.0
                val spread = max(maximum - minimum, 0.5)
                val window = max(1L, end - start)
                group.series.forEachIndexed { seriesIndex, series ->
                    val path = Path()
                    series.points.forEachIndexed { index, point ->
                        val x = ((point.occurredAtEpochMillis - start).toFloat() / window) * size.width
                        val y = size.height - (((point.value - minimum) / spread).toFloat() * size.height)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(colors[seriesIndex % colors.size], 5.dp.toPx(), Offset(x, y))
                    }
                    if (series.points.size > 1) {
                        drawPath(path, colors[seriesIndex % colors.size], style = Stroke(3.dp.toPx()))
                    }
                }
                annotations.forEach { marker ->
                    val x = ((marker.occurredAtEpochMillis - start).toFloat() / window) * size.width
                    drawLine(
                        color = eventColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    )
                }
            }
            group.series.forEachIndexed { index, series ->
                Text("● ${series.definition.parameter.label()}", color = colors[index % colors.size])
            }
        }
    }
}

@Composable
private fun BeforeAfterChart(
    parameterName: String,
    comparison: InterventionSeriesComparison.Available,
) {
    val beforeColor = MaterialTheme.colorScheme.secondaryContainer
    val afterColor = MaterialTheme.colorScheme.tertiaryContainer
    val lineColor = MaterialTheme.colorScheme.primary
    val description = stringResource(
        R.string.trends_before_after_chart_description,
        parameterName,
        comparison.beforeAverage.displayTrendValue(2),
        comparison.afterAverage.displayTrendValue(2),
        comparison.unit,
    )
    Canvas(
        Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description },
    ) {
        drawRect(beforeColor, size = Size(size.width / 2f, size.height))
        drawRect(afterColor, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
        drawLine(lineColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 3.dp.toPx())
        val min = minOf(comparison.beforeAverage, comparison.afterAverage)
        val spread = max(kotlin.math.abs(comparison.afterAverage - comparison.beforeAverage), 0.5)
        fun y(value: Double) = size.height - (((value - min) / spread).toFloat() * size.height * 0.8f) - size.height * 0.1f
        val before = Offset(size.width * 0.25f, y(comparison.beforeAverage))
        val after = Offset(size.width * 0.75f, y(comparison.afterAverage))
        drawLine(lineColor, before, after, 4.dp.toPx())
        drawCircle(lineColor, 6.dp.toPx(), before)
        drawCircle(lineColor, 6.dp.toPx(), after)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.trends_before_label))
        Text(stringResource(R.string.trends_intervention_marker))
        Text(stringResource(R.string.trends_after_label))
    }
}

@Composable
private fun DescriptiveTrendDirection.label(): String = stringResource(
    when (this) {
        DescriptiveTrendDirection.RISING -> R.string.trends_direction_rising
        DescriptiveTrendDirection.FALLING -> R.string.trends_direction_falling
        DescriptiveTrendDirection.UNCHANGED -> R.string.trends_direction_unchanged
        DescriptiveTrendDirection.SINGLE_VALUE -> R.string.trends_direction_single
        DescriptiveTrendDirection.NO_DATA -> R.string.trends_direction_no_data
    },
)

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

private fun Double.displayTrendValue(precision: Int): String {
    return java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = precision
        isGroupingUsed = false
    }.format(this)
}
