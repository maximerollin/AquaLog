package com.maximerollin.aqualog.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.label
import com.maximerollin.aqualog.regionalDecimalSeparator
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.enabled_for_parameter
import com.maximerollin.aqualog.resources.parameter_maximum
import com.maximerollin.aqualog.resources.parameter_minimum
import com.maximerollin.aqualog.resources.parameter_precision
import com.maximerollin.aqualog.resources.parameter_unit
import com.maximerollin.aqualog.resources.range_sheet_decrease
import com.maximerollin.aqualog.resources.range_sheet_done
import com.maximerollin.aqualog.resources.range_sheet_hint
import com.maximerollin.aqualog.resources.range_sheet_increase
import com.maximerollin.aqualog.resources.range_sheet_invalid
import com.maximerollin.aqualog.ui.icon.AquagendaIcons
import com.maximerollin.aqualog.ui.icon.Minus
import com.maximerollin.aqualog.ui.icon.Plus
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.dmSansFamily
import com.maximerollin.aqualog.ui.theme.frauncesFamily
import java.math.BigDecimal
import java.math.RoundingMode
import org.jetbrains.compose.resources.stringResource

/**
 * [input] moved by one step of 10^−[precision] in [direction], kept at 0 or more and written with exactly [precision] decimals
 * and [decimalSeparator]. Blank counts as 0; an unparsable input gives null, so the button does nothing.
 */
fun stepRangeInput(input: String, precision: Int, direction: Int, decimalSeparator: Char): String? {
    val current = if (input.isBlank()) BigDecimal.ZERO else input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    val step = BigDecimal.ONE.movePointLeft(precision).multiply(BigDecimal(direction))
    return current.add(step).max(BigDecimal.ZERO).setScale(precision, RoundingMode.HALF_UP)
        .toPlainString().replace('.', decimalSeparator)
}

/** The decimals one step in [direction], within 0..3; an unparsable input becomes 1. */
fun stepPrecision(input: String, direction: Int): String {
    val current = input.trim().toIntOrNull() ?: return "1"
    return (current + direction).coerceIn(0, 3).toString()
}

/** The range of one Parameter in a sheet: min and max as steppers that still take typing, the unit, the decimals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSheet(state: ParameterEditorState, actions: OnboardingActions, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val parameter = state.parameter
    val label = parameter.label()
    // The steppers move by the typed decimals; an invalid value steps by tenths until it is fixed.
    val precision = state.precision.trim().toIntOrNull()?.coerceIn(0, 3) ?: 1
    val enabledDescription = stringResource(Res.string.enabled_for_parameter, label)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("range-sheet"),
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = TextStyle(fontFamily = frauncesFamily(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { actions.setParameterActive(parameter, it) },
                    modifier = Modifier.semantics { contentDescription = enabledDescription },
                )
            }
            Text(stringResource(Res.string.range_sheet_hint), fontSize = 13.sp, color = colors.onSurfaceVariant)
            RangeStepperRow(
                label = stringResource(Res.string.parameter_minimum),
                value = state.indicativeMinimum,
                precision = precision,
                onValueChange = { actions.updateParameterMinimum(parameter, it) },
                tag = "range-min",
            )
            RangeStepperRow(
                label = stringResource(Res.string.parameter_maximum),
                value = state.indicativeMaximum,
                precision = precision,
                onValueChange = { actions.updateParameterMaximum(parameter, it) },
                tag = "range-max",
            )
            FilledField(
                value = state.unit,
                onValueChange = { actions.updateParameterUnit(parameter, it) },
                label = stringResource(Res.string.parameter_unit),
                modifier = Modifier.fillMaxWidth(),
            )
            val precisionLabel = stringResource(Res.string.parameter_precision)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(precisionLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                StepButton(AquagendaIcons.Minus, stringResource(Res.string.range_sheet_decrease, precisionLabel)) {
                    actions.updateParameterPrecision(parameter, stepPrecision(state.precision, -1))
                }
                Text(
                    state.precision,
                    modifier = Modifier.widthIn(min = 48.dp),
                    style = TextStyle(fontFamily = dmSansFamily(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    color = colors.onSurface,
                )
                StepButton(AquagendaIcons.Plus, stringResource(Res.string.range_sheet_increase, precisionLabel)) {
                    actions.updateParameterPrecision(parameter, stepPrecision(state.precision, 1))
                }
            }
            if (state.toDraft(0) == null) {
                Text(stringResource(Res.string.range_sheet_invalid), fontSize = 13.sp, color = colors.error)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(Res.string.range_sheet_done))
            }
        }
    }
}

/** [label], then − / an editable number / +; the buttons step at [precision] and leave an unparsable value alone. */
@Composable
private fun RangeStepperRow(label: String, value: String, precision: Int, onValueChange: (String) -> Unit, tag: String) {
    val colors = MaterialTheme.colorScheme
    val step = { direction: Int ->
        stepRangeInput(value, precision, direction, regionalDecimalSeparator())?.let(onValueChange)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The label stays its own node, read just before the buttons (which name it too) and the field.
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
        StepButton(AquagendaIcons.Minus, stringResource(Res.string.range_sheet_decrease, label)) { step(-1) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // 72 dp, grown with the font scale so large fonts keep the whole number in view.
            modifier = Modifier
                .width(72.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
                .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp)
                .testTag(tag),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                fontFamily = dmSansFamily(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
                color = colors.onSurface,
            ),
            cursorBrush = SolidColor(colors.primary),
        )
        StepButton(AquagendaIcons.Plus, stringResource(Res.string.range_sheet_increase, label)) { step(1) }
    }
}

/** A 40 dp round button in a 48 dp target. */
@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(icon, contentDescription = description)
    }
}
