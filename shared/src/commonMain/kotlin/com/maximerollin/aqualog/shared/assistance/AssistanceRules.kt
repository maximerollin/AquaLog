package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.isOutsideIndicativeRange
import kotlin.math.abs

enum class ReadingLevel { OK, ATTENTION, ALERT }

/** Alert thresholds are inclusive; [jump] is the absolute change that deserves a second look. */
data class ParameterRule(
    val alertBelow: Double?,
    val alertAbove: Double?,
    val plausible: ClosedFloatingPointRange<Double>,
    val jump: Double,
)

fun ruleFor(parameter: BuiltInParameter): ParameterRule = when (parameter) {
    BuiltInParameter.TEMPERATURE -> ParameterRule(18.0, 31.0, 0.0..45.0, 4.0)
    BuiltInParameter.PH -> ParameterRule(6.0, 8.5, 0.0..14.0, 1.0)
    BuiltInParameter.AMMONIA -> ParameterRule(null, 0.5, 0.0..10.0, 1.0)
    BuiltInParameter.NITRITE -> ParameterRule(null, 0.5, 0.0..10.0, 1.0)
    BuiltInParameter.NITRATE -> ParameterRule(null, 80.0, 0.0..500.0, 40.0)
    BuiltInParameter.GH -> ParameterRule(2.0, 20.0, 0.0..50.0, 6.0)
    BuiltInParameter.KH -> ParameterRule(1.0, 15.0, 0.0..50.0, 4.0)
    BuiltInParameter.TDS -> ParameterRule(null, 600.0, 0.0..3000.0, 200.0)
    BuiltInParameter.PHOSPHATE -> ParameterRule(null, 2.0, 0.0..20.0, 1.5)
    BuiltInParameter.SALINITY -> ParameterRule(null, null, 0.0..50.0, 5.0)
}

fun readingLevel(definition: ParameterDefinition, value: Double): ReadingLevel {
    val rule = ruleFor(definition.parameter)
    return when {
        rule.alertBelow?.let { value <= it } == true || rule.alertAbove?.let { value >= it } == true -> ReadingLevel.ALERT
        definition.isOutsideIndicativeRange(value) -> ReadingLevel.ATTENTION
        else -> ReadingLevel.OK
    }
}

sealed interface MeasurementConcern {
    /** [suggestion] is the value with a likely decimal slip fixed, or null when none fits. */
    data class Implausible(val suggestion: Double?) : MeasurementConcern

    data class BigJump(val previous: Double, val previousAtEpochMillis: Long) : MeasurementConcern
}

private const val BIG_JUMP_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000

fun checkMeasurement(
    definition: ParameterDefinition,
    value: Double,
    previous: Double?,
    previousAtEpochMillis: Long?,
    nowEpochMillis: Long,
): MeasurementConcern? {
    val rule = ruleFor(definition.parameter)
    if (value !in rule.plausible) {
        // A null range never rejects a candidate, so the plausible range alone decides then.
        val suggestion = listOf(value / 10, value / 100)
            .firstOrNull { it in rule.plausible && !definition.isOutsideIndicativeRange(it) }
        return MeasurementConcern.Implausible(suggestion)
    }
    if (previous != null && previousAtEpochMillis != null &&
        nowEpochMillis - previousAtEpochMillis <= BIG_JUMP_WINDOW_MILLIS &&
        abs(value - previous) > rule.jump
    ) {
        return MeasurementConcern.BigJump(previous, previousAtEpochMillis)
    }
    return null
}
