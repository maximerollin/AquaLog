package com.maximerollin.aqualog

import com.maximerollin.aqualog.ui.theme.ValueTileMinWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.shared.assistance.ruleFor
import com.maximerollin.aqualog.shared.evaluateMeasurement
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import com.maximerollin.aqualog.ui.theme.LevelMark
import com.maximerollin.aqualog.ui.theme.levelContainerColor
import com.maximerollin.aqualog.ui.theme.levelTextColor
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

enum class SessionTileState { EMPTY, INVALID, IN_RANGE, ABOVE, BELOW, FAR_ABOVE, FAR_BELOW, NO_RANGE }

/** A tile's [state] in words and its [level], the tint and mark, from one reading of the input. */
internal data class SessionTileReading(val state: SessionTileState, val level: ReadingLevel)

internal fun sessionTileReading(input: String, definition: ParameterDefinition, profile: AquariumProfile): SessionTileReading {
    if (input.isBlank()) return SessionTileReading(SessionTileState.EMPTY, ReadingLevel.OK)
    val feedback = evaluateMeasurement(input, definition)
    val value = feedback.value
    if (!feedback.isValid || value == null) return SessionTileReading(SessionTileState.INVALID, ReadingLevel.OK)
    val level = readingLevel(definition, value, profile)
    return SessionTileReading(rangeState(value, definition, level), level)
}

/** A valid [value] against the parameter's indicative range, in the words the tiles and the Trends hero share. */
internal fun valueRangeState(value: Double, definition: ParameterDefinition, profile: AquariumProfile): SessionTileState =
    rangeState(value, definition, readingLevel(definition, value, profile))

private fun rangeState(value: Double, definition: ParameterDefinition, level: ReadingLevel): SessionTileState {
    val minimum = definition.indicativeMinimum
    val maximum = definition.indicativeMaximum
    val far = level == ReadingLevel.ALERT
    return when {
        maximum != null && value > maximum -> if (far) SessionTileState.FAR_ABOVE else SessionTileState.ABOVE
        minimum != null && value < minimum -> if (far) SessionTileState.FAR_BELOW else SessionTileState.BELOW
        // An alert beyond a bound the person left empty: its side comes from the alert thresholds, so a tinted tile
        // never reads « In range » nor stays wordless.
        level != ReadingLevel.OK -> {
            val below = ruleFor(definition.parameter).alertBelow?.let { value <= it } == true
            when {
                below -> if (far) SessionTileState.FAR_BELOW else SessionTileState.BELOW
                else -> if (far) SessionTileState.FAR_ABOVE else SessionTileState.ABOVE
            }
        }
        minimum == null && maximum == null -> SessionTileState.NO_RANGE
        else -> SessionTileState.IN_RANGE
    }
}

internal fun sessionTileState(input: String, definition: ParameterDefinition, profile: AquariumProfile): SessionTileState =
    sessionTileReading(input, definition, profile).state

internal fun sessionColumns(fontScale: Float, maxWidth: Dp): Int =
    if (fontScale >= 1.3f || maxWidth < 340.dp) 1 else 2

/** The gap between two tiles of a [MeasureTileRow]. */
internal val MeasureTileGap = 10.dp

/** The narrowest width that holds three [ValueTile]s at their minimum width, with the two gaps between them: 308 dp. */
internal val ThreeDetailColumnsMinWidth = ValueTileMinWidth * 3 + MeasureTileGap * 2

/** Columns of the Session details tiles: three, or two at large text or when three would go below [ValueTileMinWidth]. */
internal fun sessionDetailsColumns(fontScale: Float, maxWidth: Dp): Int =
    if (fontScale > 1.3f || maxWidth < ThreeDetailColumnsMinWidth) 2 else 3

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

/** « 6,5–7,5 », « ≤ 40 », « ≥ 3 », or nothing when the parameter has no indicative range. */
internal fun ParameterDefinition.rangeText(formatter: LocalizedFormatters): String? {
    val min = indicativeMinimum?.let { formatter.number(it, precision) }
    val max = indicativeMaximum?.let { formatter.number(it, precision) }
    return when {
        min != null && max != null -> "$min–$max"
        max != null -> "≤ $max"
        min != null -> "≥ $min"
        else -> null
    }
}

/**
 * One measurement of a Session: the tile is the text field itself (its decoration), so a tap anywhere on it focuses the
 * numeric keyboard and the tile stays one accessible node. Name and range, the big value with its unit, then the state in
 * words; out of range, the tile is tinted and marked by a shape, so colour never carries the state alone.
 */
@Composable
fun MeasureTile(
    definition: ParameterDefinition,
    input: String,
    profile: AquariumProfile,
    formatter: LocalizedFormatters,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (state, level) = sessionTileReading(input, definition, profile)
    val invalid = state == SessionTileState.INVALID
    val container = levelContainerColor(level)
    val levelColor = levelTextColor(level)
    // On a tinted tile the muted grey gives way to the level's own text colour, as on the home's value tiles.
    val muted = if (container != null && levelColor != null) levelColor else colors.onSurfaceVariant
    val valueColor = if (invalid) colors.error else levelColor ?: colors.onSurface
    val stateColor = when (state) {
        SessionTileState.EMPTY, SessionTileState.NO_RANGE -> colors.onSurfaceVariant
        SessionTileState.INVALID -> colors.error
        SessionTileState.IN_RANGE -> colors.primary
        else -> levelColor ?: colors.onSurfaceVariant
    }
    val name = definition.parameter.label()
    val unit = definition.unit
    val shownUnit = unit.takeIf { it.isNotBlank() && it != name } // pH's unit is pH
    val range = definition.rangeText(formatter)
    val stateText = state.text()
    val description = stringResource(Res.string.measurement_for_parameter, name)
    val invalidText = stringResource(Res.string.invalid_measurement)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    val (borderWidth, borderColor) = when {
        invalid -> 2.dp to colors.error
        focused -> 2.dp to colors.primary
        else -> 1.dp to colors.outlineVariant
    }
    val valueStyle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        color = valueColor,
    )
    // The field is at least ten characters wide: the unit follows the typed text instead, measured in the field's own style.
    val textMeasurer = rememberTextMeasurer()
    val placeholderStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 28.sp, lineHeight = 34.sp))
    val valueWidth = with(LocalDensity.current) {
        val text = if (input.isEmpty()) {
            textMeasurer.measure(PLACEHOLDER, placeholderStyle, softWrap = false, maxLines = 1)
        } else {
            textMeasurer.measure(input, valueStyle, softWrap = false, maxLines = 1)
        }
        ceil(text.getLineRight(0)).toDp() + CursorRoom
    }
    BasicTextField(
        value = input,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            contentDescription = description
            // The visible lines are silent, so each word is heard once: the name in the description, then the unit, the
            // state (above / below says the level) and the range. An invalid input is said by the error alone.
            stateDescription = listOf(shownUnit.orEmpty(), if (invalid) "" else stateText.orEmpty(), range.orEmpty())
                .filter(String::isNotBlank).joinToString(", ")
            if (invalid) error(invalidText)
        },
        singleLine = true,
        interactionSource = interaction,
        textStyle = valueStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        decorationBox = { inner ->
            Column(
                Modifier.fillMaxWidth().fillMaxHeight()
                    .background(container ?: colors.surface, shape)
                    .border(borderWidth, borderColor, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (level != ReadingLevel.OK && levelColor != null) {
                        LevelMark(level, levelColor, Modifier.padding(end = 4.dp).testTag(LEVEL_MARK_TAG))
                    }
                    Text(
                        buildAnnotatedString {
                            append(name)
                            if (range != null) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = muted)) { append(SEPARATOR + range) }
                            }
                        },
                        modifier = Modifier.clearAndSetSemantics {},
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                }
                Row(Modifier.padding(top = 2.dp)) {
                    // Only as wide as the value (or « — ») plus the cursor, so the unit follows it; past the room left, the
                    // field scrolls and the unit stays in view.
                    Box(Modifier.weight(1f, fill = false).widthIn(max = valueWidth).alignByBaseline()) {
                        if (input.isEmpty()) {
                            Text(
                                text = PLACEHOLDER, style = placeholderStyle, color = colors.outline,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                        inner()
                    }
                    if (shownUnit != null) {
                        // Visual only, like the other lines: the field's state description says it. Its tight line height keeps it, once
                        // shifted onto the value's baseline, inside the value's line: the row's intrinsic height ignores that shift.
                        Text(
                            text = shownUnit, fontSize = 14.sp, lineHeight = 16.sp, color = muted,
                            modifier = Modifier.padding(start = UnitGap - CursorRoom).alignByBaseline()
                                .testTag(SESSION_TILE_UNIT_TAG).clearAndSetSemantics {},
                        )
                    }
                }
                if (stateText != null) {
                    Text(
                        stateText,
                        Modifier.padding(top = 2.dp).testTag(SESSION_TILE_STATE_TAG).clearAndSetSemantics {},
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = stateColor,
                    )
                }
            }
        },
    )
}

/** The gap between the value and its unit. */
private val UnitGap = 4.dp

/** Kept after the value for the 2 dp cursor, so a field just as wide as its text never scrolls it. */
private val CursorRoom = 2.dp

private const val PLACEHOLDER = "—"

/** « Next » on every tile but the last, which says « Done » and closes the keyboard. */
internal fun sessionImeAction(index: Int, lastIndex: Int): ImeAction = if (index >= lastIndex) ImeAction.Done else ImeAction.Next

/** Test tag of a tile's state line, which is silent: its words are in the tile's state description. */
internal const val SESSION_TILE_STATE_TAG = "session-tile-state"

/** Test tag of a tile's unit, silent too: the state description says it. */
internal const val SESSION_TILE_UNIT_TAG = "session-tile-unit"

/**
 * One row of the Session grid: [tiles] share the width equally, 10 dp apart, and the same height. A short last row keeps
 * an empty slot per missing tile, so a lone tile stays one column wide; [columns] is the grid's column count.
 */
@Composable
fun MeasureTileRow(tiles: List<@Composable (Modifier) -> Unit>, columns: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MeasureTileGap)) {
        tiles.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
        repeat(columns - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}
