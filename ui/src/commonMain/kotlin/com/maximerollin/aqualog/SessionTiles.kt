package com.maximerollin.aqualog

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.shared.evaluateMeasurement
import org.jetbrains.compose.resources.stringResource

enum class SessionTileState { EMPTY, INVALID, IN_RANGE, ABOVE, BELOW, FAR_ABOVE, FAR_BELOW, NO_RANGE }

internal fun sessionTileState(input: String, definition: ParameterDefinition, profile: AquariumProfile): SessionTileState {
    if (input.isBlank()) return SessionTileState.EMPTY
    val feedback = evaluateMeasurement(input, definition)
    val value = feedback.value
    if (!feedback.isValid || value == null) return SessionTileState.INVALID
    val minimum = definition.indicativeMinimum
    val maximum = definition.indicativeMaximum
    if (minimum == null && maximum == null) return SessionTileState.NO_RANGE
    val far = readingLevel(definition, value, profile) == ReadingLevel.ALERT
    return when {
        maximum != null && value > maximum -> if (far) SessionTileState.FAR_ABOVE else SessionTileState.ABOVE
        minimum != null && value < minimum -> if (far) SessionTileState.FAR_BELOW else SessionTileState.BELOW
        else -> SessionTileState.IN_RANGE
    }
}

internal fun sessionColumns(fontScale: Float, maxWidth: Dp): Int =
    if (fontScale >= 1.3f || maxWidth < 340.dp) 1 else 2

internal fun validMeasurementCount(inputs: Map<String, String>, definitions: List<ParameterDefinition>): Int =
    definitions.count { definition ->
        val input = inputs[definition.id].orEmpty()
        input.isNotBlank() && evaluateMeasurement(input, definition).isValid
    }

@Composable
internal fun SessionTileState.text(): String? = when (this) {
    SessionTileState.NO_RANGE -> null
    SessionTileState.EMPTY -> stringResource(Res.string.session_tile_tap_to_enter)
    SessionTileState.INVALID -> stringResource(Res.string.invalid_measurement)
    SessionTileState.IN_RANGE -> stringResource(Res.string.session_tile_in_range)
    SessionTileState.ABOVE -> stringResource(Res.string.session_tile_above)
    SessionTileState.BELOW -> stringResource(Res.string.session_tile_below)
    SessionTileState.FAR_ABOVE -> stringResource(Res.string.session_tile_far_above)
    SessionTileState.FAR_BELOW -> stringResource(Res.string.session_tile_far_below)
}
