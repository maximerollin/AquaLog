package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.close
import com.maximerollin.aqualog.resources.date_format_hint
import com.maximerollin.aqualog.resources.trends_apply_custom_period
import com.maximerollin.aqualog.resources.trends_complete_history
import com.maximerollin.aqualog.resources.trends_custom_period
import com.maximerollin.aqualog.resources.trends_end_date
import com.maximerollin.aqualog.resources.trends_exact_values
import com.maximerollin.aqualog.resources.trends_hero_change_days
import com.maximerollin.aqualog.resources.trends_hero_legend
import com.maximerollin.aqualog.resources.trends_invalid_custom_period
import com.maximerollin.aqualog.resources.trends_row_collapsed
import com.maximerollin.aqualog.resources.trends_row_expanded
import com.maximerollin.aqualog.resources.trends_start_date
import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendSnapshot
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import com.maximerollin.aqualog.ui.theme.LevelMark
import com.maximerollin.aqualog.ui.theme.levelContainerColor
import com.maximerollin.aqualog.ui.theme.levelTextColor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val DAY_MILLIS = 86_400_000.0

/** Test tags of the hero's state chip and big value, so a test can check they never overlap. */
internal const val TREND_HERO_STATE_TAG = "trend-hero-state"
internal const val TREND_HERO_VALUE_TAG = "trend-hero-value"

/**
 * The parameter the Trends hero shows: the first, by position, whose latest value is outside its range; else the first
 * with a measurement; else none.
 */
internal fun heroSeries(snapshot: TrendSnapshot): ParameterTrendSeries? {
    val ordered = snapshot.series.sortedBy { it.definition.position }
    val profile = snapshot.aquarium.profile
    return ordered.firstOrNull { series ->
        val last = series.points.lastOrNull() ?: return@firstOrNull false
        when (valueRangeState(last.value, series.definition, profile)) {
            SessionTileState.IN_RANGE, SessionTileState.NO_RANGE -> false
            else -> true
        }
    } ?: ordered.firstOrNull { it.points.isNotEmpty() }
}

/** The last value minus the first over the period, or null with fewer than two points. */
internal fun periodChange(series: ParameterTrendSeries): Double? {
    if (series.points.size < 2) return null
    return series.points.last().value - series.points.first().value
}

/** The length of the snapshot's window in whole days, at least 1. */
internal fun windowDays(snapshot: TrendSnapshot): Int =
    ((snapshot.windowEndEpochMillis - snapshot.windowStartEpochMillis) / DAY_MILLIS).roundToInt().coerceAtLeast(1)

/** « +0.7 », « -0.3 » or « 0 » at [precision]: a change that rounds to nothing carries no sign. */
private fun signedChange(change: Double, precision: Int, formatter: LocalizedFormatters): String {
    val scale = 10.0.pow(precision)
    val rounded = (change * scale).roundToLong() / scale
    if (rounded == 0.0) return formatter.number(0.0, precision)
    val shown = formatter.number(rounded, precision)
    return if (rounded > 0) "+$shown" else shown
}

/**
 * The parameter to watch: name and state in words, the last value in large, its change over the period, the full chart
 * with the range band and the water changes, and the toggle for the exact values that follow the card.
 */
@Composable
internal fun TrendHeroCard(
    series: ParameterTrendSeries,
    snapshot: TrendSnapshot,
    expanded: Boolean,
    onToggleValues: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val formatter = currentLocalizedFormatters()
    val definition = series.definition
    val label = definition.parameter.label()
    val unit = definition.unit.takeIf { it.isNotBlank() && it != label } // pH's unit is pH
    val last = series.points.lastOrNull()
    val level = last?.let { readingLevel(definition, it.value, snapshot.aquarium.profile) } ?: ReadingLevel.OK
    val stateWords = last?.let { valueRangeState(it.value, definition, snapshot.aquarium.profile).text() }
    val valueColor = levelTextColor(level) ?: colors.onSurface
    val change = periodChange(series)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().shadow(4.dp, shape).background(colors.surface, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeroTitleRow(label, stateWords, level)
        if (last != null) {
            Text(
                buildAnnotatedString {
                    append(formatter.number(last.value, definition.precision))
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal)) { append(" $unit") }
                    }
                },
                modifier = Modifier.testTag(TREND_HERO_VALUE_TAG),
                color = valueColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        if (change != null) {
            val days = windowDays(snapshot)
            Text(
                pluralStringResource(
                    Res.plurals.trends_hero_change_days, days, signedChange(change, definition.precision, formatter), days,
                ),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TrendChart(series, snapshot, framed = false)
        Text(stringResource(Res.string.trends_hero_legend), fontSize = 11.sp, color = colors.onSurfaceVariant)
        val toggleState = stringResource(if (expanded) Res.string.trends_row_expanded else Res.string.trends_row_collapsed)
        TextButton(
            onClick = onToggleValues,
            modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)
                .semantics { stateDescription = toggleState },
        ) {
            Text(
                stringResource(Res.string.trends_exact_values),
                fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** The parameter's name and, at the end, its state as a chip; the chip wraps under the name when both do not fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTitleRow(label: String, stateWords: String?, level: ReadingLevel) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.padding(end = 8.dp).semantics { heading() },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (stateWords != null) StateChip(stateWords, level)
    }
}

/** A [com.maximerollin.aqualog.ui.theme.ValuePill]-styled chip whose words are read: the state never rests on colour alone. */
@Composable
private fun StateChip(words: String, level: ReadingLevel) {
    val colors = MaterialTheme.colorScheme
    val container = levelContainerColor(level)
    val textColor = levelTextColor(level) ?: colors.onSurface
    Row(
        Modifier.testTag(TREND_HERO_STATE_TAG)
            .background(container ?: colors.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level != ReadingLevel.OK) LevelMark(level, textColor, Modifier.padding(end = 4.dp).testTag(LEVEL_MARK_TAG))
        Text(words, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

/** Pro choices that used to sit at the bottom of the list: the complete history, or a period between two dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPeriodSheet(state: HomeUiState, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val trends = state.trends
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CarnetSectionTitle(
                stringResource(Res.string.trends_custom_period),
                actionLabel = stringResource(Res.string.close),
                onAction = onDismiss,
            )
            Button(
                onClick = {
                    viewModel.showCompleteProHistory()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.trends_complete_history)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = trends.customStartDate,
                    onValueChange = viewModel::updateTrendCustomStart,
                    label = { Text(stringResource(Res.string.trends_start_date)) },
                    supportingText = { Text(stringResource(Res.string.date_format_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = trends.customEndDate,
                    onValueChange = viewModel::updateTrendCustomEnd,
                    label = { Text(stringResource(Res.string.trends_end_date)) },
                    supportingText = { Text(stringResource(Res.string.date_format_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    viewModel.applyTrendCustomPeriod()
                    // A valid period closes the sheet; an invalid one keeps it open on its error.
                    if (!viewModel.uiState.value.trends.customDateError) onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.trends_apply_custom_period)) }
            if (trends.customDateError) {
                Text(stringResource(Res.string.trends_invalid_custom_period), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
