package com.maximerollin.aqualog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.reading_attention_description
import com.maximerollin.aqualog.resources.trends_chart_description
import com.maximerollin.aqualog.resources.trends_no_measurements
import com.maximerollin.aqualog.resources.trends_row_collapsed
import com.maximerollin.aqualog.resources.trends_row_expanded
import com.maximerollin.aqualog.resources.trends_row_hide_chart
import com.maximerollin.aqualog.resources.trends_row_show_chart
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendSnapshot
import com.maximerollin.aqualog.shared.isOutsideIndicativeRange
import com.maximerollin.aqualog.ui.theme.attentionMarkColor
import com.maximerollin.aqualog.ui.theme.attentionTextColor
import org.jetbrains.compose.resources.stringResource

/** Value scale shared by the line and the range band: min/max over the points and the indicative range. */
private fun ParameterTrendSeries.sparklineScale(): Pair<Double, Double> {
    val values = points.map { it.value } + listOfNotNull(definition.indicativeMinimum, definition.indicativeMaximum)
    return (values.minOrNull() ?: 0.0) to (values.maxOrNull() ?: 0.0)
}

private fun Pair<Double, Double>.yFor(value: Double, height: Float): Float {
    val spread = second - first
    return if (spread <= 0.0) height / 2f else (height - ((value - first) / spread).toFloat() * height)
}

fun sparklinePoints(
    series: ParameterTrendSeries,
    windowStart: Long,
    windowEnd: Long,
    width: Float,
    height: Float,
): List<Offset> {
    val scale = series.sparklineScale()
    val window = maxOf(1L, windowEnd - windowStart)
    return series.points.map { point ->
        Offset((point.occurredAtEpochMillis - windowStart).toFloat() / window * width, scale.yFor(point.value, height))
    }
}

@Composable
fun TrendSparklineRow(
    series: ParameterTrendSeries,
    snapshot: TrendSnapshot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val definition = series.definition
    val label = definition.parameter.label()
    val latest = series.points.lastOrNull()
    val attention = latest != null && definition.isOutsideIndicativeRange(latest.value)
    val chartDescription = if (latest == null) {
        stringResource(Res.string.trends_no_measurements)
    } else {
        stringResource(Res.string.trends_chart_description, label)
    }
    val attentionCue = stringResource(Res.string.reading_attention_description)
    val unit = definition.unit.takeIf { it.isNotBlank() && it != label }
    val valueText = latest?.value?.let { currentLocalizedFormatters().number(it, definition.precision) }
    // Merged descriptions replace the visible Text for screen readers, so the reading and the name are spoken here;
    // the miniature's own description is merged after it.
    val spokenReading = listOfNotNull(
        listOfNotNull(valueText, unit).joinToString(" ").takeIf { it.isNotEmpty() },
        label,
        attentionCue.takeIf { attention },
    ).joinToString(", ")
    val stateText = stringResource(if (selected) Res.string.trends_row_expanded else Res.string.trends_row_collapsed)
    val actionLabel = stringResource(if (selected) Res.string.trends_row_hide_chart else Res.string.trends_row_show_chart)
    val lineColor = if (attention) attentionMarkColor() else MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.primaryContainer
    val markerColor = MaterialTheme.colorScheme.primary
    val waterChanges = snapshot.events.filter { MaintenanceActionType.WATER_CHANGE in it.maintenanceActionTypes }
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("trend-row")
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenReading
                stateDescription = stateText
            }
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    valueText ?: "—",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = if (attention) attentionTextColor() else MaterialTheme.colorScheme.onSurface,
                )
                if (unit != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The expanded row shows the full chart below, so the miniature would only repeat it.
        if (!selected) {
            Canvas(
                Modifier
                    .weight(3f)
                    .height(40.dp)
                    .semantics { contentDescription = chartDescription },
            ) {
                val inset = 4.dp.toPx()
                val width = size.width - 2 * inset
                val height = size.height - 2 * inset
                val scale = series.sparklineScale()
                val min = definition.indicativeMinimum
                val max = definition.indicativeMaximum
                if (min != null && max != null) {
                    val top = scale.yFor(max, height) + inset
                    val bottom = scale.yFor(min, height) + inset
                    val bandHeight = maxOf(bottom - top, 2.dp.toPx())
                    drawRect(bandColor, Offset(0f, top - (bandHeight - (bottom - top)) / 2f), Size(size.width, bandHeight))
                }
                val points = sparklinePoints(series, snapshot.windowStartEpochMillis, snapshot.windowEndEpochMillis, width, height)
                    .map { Offset(it.x + inset, it.y + inset) }
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, lineColor, style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                points.lastOrNull()?.let { drawCircle(lineColor, 2.6.dp.toPx(), it) }
                val window = maxOf(1L, snapshot.windowEndEpochMillis - snapshot.windowStartEpochMillis)
                waterChanges
                    .filter { it.occurredAtEpochMillis in snapshot.windowStartEpochMillis..snapshot.windowEndEpochMillis }
                    .forEach { event ->
                        val x = (event.occurredAtEpochMillis - snapshot.windowStartEpochMillis).toFloat() / window * width + inset
                        val half = 3.dp.toPx()
                        val baseline = size.height - 1.dp.toPx()
                        drawPath(
                            Path().apply {
                                moveTo(x, baseline - 5.dp.toPx())
                                lineTo(x + half, baseline)
                                lineTo(x - half, baseline)
                                close()
                            },
                            markerColor,
                        )
                    }
            }
        }
    }
}
