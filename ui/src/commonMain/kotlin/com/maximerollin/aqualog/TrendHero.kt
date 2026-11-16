package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendSnapshot
import kotlin.math.roundToInt

private const val DAY_MILLIS = 86_400_000.0

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
