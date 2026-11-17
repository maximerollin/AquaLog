package com.maximerollin.aqualog

import com.maximerollin.aqualog.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Calendar
import com.maximerollin.aqualog.ui.theme.CarnetDivider
import com.maximerollin.aqualog.ui.theme.CarnetScreenTitle
import com.maximerollin.aqualog.ui.theme.FloatingNavBarClearance
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.PeriodSegmented
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
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
import kotlin.math.max

/**
 * Trends in the immersive style: the water header with the custom-period button, then on the paper sheet the period, the
 * hero card for the parameter to watch, the other parameters as rows, the history limit and the Pro analysis.
 * Draws under the status bar and clears the floating bar itself.
 */
@Composable
fun TrendsScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val trends = state.trends
    val snapshot = trends.snapshot
    val title = stringResource(Res.string.trends_title)
    val subtitle = (snapshot?.aquarium ?: state.setup?.aquarium)?.name.orEmpty()
    val sceneAquarium = snapshot?.aquarium ?: state.setup?.aquarium ?: state.aquariums.firstOrNull()
    val customLabel = stringResource(Res.string.trends_custom_period)
    val periodLabels = TrendPeriod.entries.map { stringResource(it.labelRes()) }
    val listState = rememberLazyListState()
    var showCustomPeriod by rememberSaveable { mutableStateOf(false) }
    val onCustomPeriod = {
        if (state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE) showCustomPeriod = true else viewModel.openProPaywall()
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("trend-list"),
            contentPadding = PaddingValues(bottom = FloatingNavBarClearance.dp),
        ) {
            item(key = "header") {
                if (sceneAquarium != null) {
                    WaterHeader(
                        aquarium = sceneAquarium,
                        title = title,
                        subtitle = subtitle,
                        action = { GlassIconButton(AquagendaIcons.Calendar, customLabel, onCustomPeriod) },
                        // The sheet's top edge, the next item, covers the water's bottom.
                        modifier = Modifier.overlapNext(30.dp),
                    )
                } else {
                    // shortcut: no Aquarium to draw the water from; the shell only opens with a setup, so this is never seen.
                    Column {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        CarnetScreenTitle(
                            title, subtitle, Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                            action = { GlassIconButton(AquagendaIcons.Calendar, customLabel, onCustomPeriod) },
                        )
                    }
                }
            }
            item(key = "sheet-top") { PaperSheetTop() }
            paperItem("period", top = 0.dp) {
                PeriodSegmented(
                    labels = periodLabels,
                    selectedIndex = TrendPeriod.entries.indexOf(trends.period),
                    onSelect = { viewModel.selectTrendPeriod(TrendPeriod.entries[it]) },
                )
            }
            when {
                trends.isLoading -> paperItem("loading") { TrendLoading(Modifier.padding(vertical = 32.dp)) }
                trends.hasRecoverableError -> paperItem("error") { TrendError(viewModel::retryTrends, Modifier) }
                else -> trendContent(state, viewModel)
            }
        }
        StatusBarOverHeader(listState)
    }
    if (showCustomPeriod) {
        CustomPeriodSheet(state, viewModel, onDismiss = { showCustomPeriod = false })
    }
}

/** The sheet's body once the trends are loaded: hero, rows, legend, history limit and Pro analysis. */
private fun LazyListScope.trendContent(state: HomeUiState, viewModel: HomeViewModel) {
    val trends = state.trends
    val snapshot = trends.snapshot
    val hero = snapshot?.let(::heroSeries)
    if (snapshot != null && hero != null) {
        val heroExpanded = hero.definition.id == trends.selectedParameterDefinitionId
        paperItem("trend-hero") {
            TrendHeroCard(
                series = hero,
                snapshot = snapshot,
                expanded = heroExpanded,
                onToggleValues = { viewModel.selectTrendParameter(if (heroExpanded) null else hero.definition.id) },
            )
        }
        if (heroExpanded) trendValuesAndEvents(hero, snapshot)
    } else {
        paperItem("trend-empty") { EmptyTrendCard() }
    }
    val rows = snapshot?.series.orEmpty()
        .filter { it.definition.id != hero?.definition?.id }
        .sortedBy { it.definition.position }
    if (snapshot != null) {
        rows.forEach { series ->
            val expanded = series.definition.id == trends.selectedParameterDefinitionId
            paperItem("trend-row-${series.definition.id}") {
                Column {
                    CarnetDivider()
                    TrendSparklineRow(
                        series = series,
                        snapshot = snapshot,
                        selected = expanded,
                        onClick = { viewModel.selectTrendParameter(if (expanded) null else series.definition.id) },
                    )
                }
            }
            if (expanded) trendDetails(series, snapshot)
        }
    }
    if (rows.isNotEmpty()) {
        paperItem("trend-sparkline-legend") {
            Text(
                stringResource(Res.string.trends_sparkline_legend),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    paperItem("trend-pro") { TrendProRow(state, viewModel) }
    trends.advancedAnalysis?.let { analysis ->
        paperItem("trend-advanced") { AdvancedAnalysis(analysis, viewModel::selectTrendIntervention) }
    }
}

/** A sheet item: paper behind it edge to edge, its content 16 dp from the sides and [top] below the previous one. */
private fun LazyListScope.paperItem(key: String, top: Dp = 12.dp, content: @Composable () -> Unit) =
    item(key = key) { OnPaper(top, content) }

@Composable
private fun OnPaper(top: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp).padding(top = top),
    ) { content() }
}

/** The existing full chart, exact values and Events, shown in place under an expanded row. */
private fun LazyListScope.trendDetails(selected: ParameterTrendSeries, snapshot: TrendSnapshot) {
    val id = selected.definition.id
    paperItem("trend-heading-$id") { TrendSeriesHeading(selected) }
    paperItem("trend-chart-$id") { TrendChart(selected, snapshot) }
    paperItem("trend-legend-$id") {
        Text(
            stringResource(Res.string.trends_legend),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    trendValuesAndEvents(selected, snapshot)
}

/** The exact values and the Events of the period: under an expanded row, or after the hero card that already shows the chart. */
private fun LazyListScope.trendValuesAndEvents(selected: ParameterTrendSeries, snapshot: TrendSnapshot) {
    val id = selected.definition.id
    paperItem("trend-values-title-$id") {
        Text(stringResource(Res.string.trends_exact_values), style = MaterialTheme.typography.titleLarge)
    }
    if (selected.points.isEmpty()) {
        paperItem("trend-values-empty-$id") { EmptyTrendCard() }
    } else {
        selected.points.forEach { point ->
            paperItem("trend-point-${point.sessionId}") {
                val value = point.value.displayTrendValue(selected.definition.precision)
                val exactValueDescription = stringResource(
                    Res.string.trends_exact_value_description,
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
                        stringResource(
                            Res.string.timeline_marker_line,
                            point.occurredAtEpochMillis.displayTrendDate(),
                            stringResource(Res.string.value_with_unit, value, selected.definition.unit),
                        ),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    paperItem("trend-events-title-$id") {
        Text(stringResource(Res.string.trends_events), style = MaterialTheme.typography.titleLarge)
    }
    if (snapshot.events.isEmpty()) {
        paperItem("trend-no-events-$id") { Text(stringResource(Res.string.trends_no_events)) }
    } else {
        snapshot.events.forEach { marker ->
            paperItem("trend-event-${marker.sessionId}") {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            Res.string.timeline_marker_line,
                            marker.occurredAtEpochMillis.displayTrendDate(),
                            marker.displayText(),
                        ),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Last row of the list: the history limit and, without Pro, the paywall link. Pro choices live in [CustomPeriodSheet]. */
@Composable
private fun TrendProRow(state: HomeUiState, viewModel: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CarnetDivider()
        Text(
            stringResource(
                if (state.trends.advancedAnalysis == null) Res.string.trends_free_limit
                else Res.string.trends_pro_complete_history,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.pro.purchaseStatus != ProPurchaseStatus.ACTIVE) {
            TextButton(
                onClick = viewModel::openProPaywall,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.unlock_history_beyond_90_days))
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
        Text(stringResource(Res.string.trends_advanced_analysis), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.trends_descriptive_disclaimer),
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
            Text(stringResource(Res.string.trends_choose_intervention), style = MaterialTheme.typography.titleMedium)
            analysis.snapshot.events.forEach { marker ->
                TextButton(
                    onClick = { onSelectIntervention(marker.sessionId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("trend-intervention-${marker.sessionId}"),
                ) {
                    Text(
                        stringResource(
                            Res.string.timeline_marker_line,
                            marker.occurredAtEpochMillis.displayTrendDate(),
                            marker.displayText(),
                        ),
                    )
                }
            }
        }
        analysis.interventionComparison?.let { comparison ->
            Text(stringResource(Res.string.trends_before_after), style = MaterialTheme.typography.titleLarge)
            comparison.series.forEach { result ->
                Card(Modifier.fillMaxWidth()) {
                    val parameterName = analysis.snapshot.series.first {
                        it.definition.id == result.definitionId
                    }.definition.parameter.label()
                    val text = when (result) {
                        is InterventionSeriesComparison.Available -> stringResource(
                            Res.string.trends_before_after_values,
                            parameterName,
                            result.beforeAverage.displayTrendValue(2),
                            result.afterAverage.displayTrendValue(2),
                            result.unit,
                        )
                        is InterventionSeriesComparison.InsufficientData -> stringResource(
                            Res.string.trends_before_after_insufficient,
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
        Text(stringResource(Res.string.trends_text_alternative), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                Res.string.trends_period_description,
                analysis.snapshot.windowStartEpochMillis.displayTrendDate(),
                analysis.snapshot.windowEndEpochMillis.displayTrendDate(),
            ),
        )
        analysis.textAlternative.series.forEach { alternative ->
            val parameterName = analysis.snapshot.series.first {
                it.definition.id == alternative.definitionId
            }.definition.parameter.label()
            val values = if (alternative.pointCount == 0) {
                stringResource(Res.string.trends_text_series_no_data, parameterName, alternative.unit)
            } else {
                stringResource(
                    Res.string.trends_text_series_values,
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
                    Res.string.trends_text_annotation,
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
                        Res.string.trends_before_after_values,
                        parameterName,
                        comparison.beforeAverage.displayTrendValue(2),
                        comparison.afterAverage.displayTrendValue(2),
                        comparison.unit,
                    )
                    is InterventionSeriesComparison.InsufficientData -> stringResource(
                        Res.string.trends_before_after_insufficient,
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
        Res.string.trends_comparison_chart_description,
        seriesLabels.joinToString(),
        group.canonicalUnit,
        annotations.size,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.trends_compatible_comparison, group.canonicalUnit))
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
                Text(
                    stringResource(Res.string.trends_series_label, series.definition.parameter.label()),
                    color = colors[index % colors.size],
                )
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
        Res.string.trends_before_after_chart_description,
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
        Text(stringResource(Res.string.trends_before_label))
        Text(stringResource(Res.string.trends_intervention_marker))
        Text(stringResource(Res.string.trends_after_label))
    }
}

@Composable
private fun DescriptiveTrendDirection.label(): String = stringResource(
    when (this) {
        DescriptiveTrendDirection.RISING -> Res.string.trends_direction_rising
        DescriptiveTrendDirection.FALLING -> Res.string.trends_direction_falling
        DescriptiveTrendDirection.UNCHANGED -> Res.string.trends_direction_unchanged
        DescriptiveTrendDirection.SINGLE_VALUE -> Res.string.trends_direction_single
        DescriptiveTrendDirection.NO_DATA -> Res.string.trends_direction_no_data
    },
)

private fun TrendPeriod.labelRes() = when (this) {
    TrendPeriod.SEVEN_DAYS -> Res.string.timeline_period_7
    TrendPeriod.THIRTY_DAYS -> Res.string.timeline_period_30
    TrendPeriod.NINETY_DAYS -> Res.string.timeline_period_90
}

@Composable
private fun TrendSeriesHeading(series: ParameterTrendSeries) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.trends_parameter_heading, series.definition.parameter.label()),
            style = MaterialTheme.typography.titleLarge,
        )
        val minimum = series.definition.indicativeMinimum
        val maximum = series.definition.indicativeMaximum
        if (minimum != null && maximum != null) {
            Text(
                stringResource(
                    Res.string.trends_indicative_target,
                    minimum.displayTrendValue(series.definition.precision),
                    maximum.displayTrendValue(series.definition.precision),
                    series.definition.unit,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The full chart of one parameter: line and points, the hatched indicative range, a dashed line and diamond per Event, and a
 * filled ▲ at the foot for each water change. [framed] puts it on its own card; the hero card draws it bare.
 */
@Composable
internal fun TrendChart(series: ParameterTrendSeries, snapshot: TrendSnapshot, framed: Boolean = true) {
    if (framed) {
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) { TrendChartCanvas(series, snapshot) }
        }
    } else {
        TrendChartCanvas(series, snapshot)
    }
}

@Composable
private fun TrendChartCanvas(series: ParameterTrendSeries, snapshot: TrendSnapshot) {
    val lineColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.secondary
    val eventColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val description = stringResource(
        Res.string.trends_chart_description,
        series.definition.parameter.label(),
    )
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
            if (MaintenanceActionType.WATER_CHANGE in event.maintenanceActionTypes) {
                val half = 5.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(x, plotBottom - 8.dp.toPx())
                        lineTo(x + half, plotBottom)
                        lineTo(x - half, plotBottom)
                        close()
                    },
                    eventColor,
                )
            }
        }
    }
}

@Composable
private fun EmptyTrendCard() {
    Card(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.trends_no_measurements), modifier = Modifier.padding(20.dp))
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
        Text(stringResource(Res.string.trends_loading), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TrendError(onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.trends_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
private fun TrendEventMarker.displayText(): String {
    val actions = maintenanceActionTypes.map { it.trendLabel() }
    val eventLabels = events.map { event ->
        val type = when (event.type) {
            EventType.OBSERVATION -> stringResource(Res.string.observation)
            EventType.INCIDENT -> stringResource(Res.string.incident)
        }
        "$type: ${event.note}"
    }
    return (actions + eventLabels).joinToString(" · ")
}

@Composable
private fun MaintenanceActionType.trendLabel(): String = stringResource(
    when (this) {
        MaintenanceActionType.WATER_CHANGE -> Res.string.action_water_change
        MaintenanceActionType.FERTILIZATION -> Res.string.action_fertilization
        MaintenanceActionType.FILTER_MAINTENANCE -> Res.string.action_filter
        MaintenanceActionType.VACUUM -> Res.string.action_vacuum
        MaintenanceActionType.GLASS_CLEANING -> Res.string.action_glass
        MaintenanceActionType.PRUNING -> Res.string.action_pruning
    },
)

@Composable
private fun Long.displayTrendDate(): String = currentLocalizedFormatters().dateTime(this)

@Composable
private fun Double.displayTrendValue(precision: Int): String {
    return currentLocalizedFormatters().number(this, precision)
}
