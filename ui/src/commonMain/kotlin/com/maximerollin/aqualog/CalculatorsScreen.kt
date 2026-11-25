package com.maximerollin.aqualog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.concentrationAfterWaterChange
import com.maximerollin.aqualog.shared.doseForVolume
import com.maximerollin.aqualog.shared.litersToUsGallons
import com.maximerollin.aqualog.shared.parseDecimalInput
import com.maximerollin.aqualog.shared.tankVolumeLiters
import com.maximerollin.aqualog.shared.waterChangeVolume
import com.maximerollin.aqualog.ui.icon.*
import com.maximerollin.aqualog.ui.theme.FilledField
import com.maximerollin.aqualog.ui.theme.GlassIconButton
import com.maximerollin.aqualog.ui.theme.GroupLabel
import com.maximerollin.aqualog.ui.theme.PaperSheetTop
import com.maximerollin.aqualog.ui.theme.WaterHeader
import com.maximerollin.aqualog.ui.theme.overlapNext
import org.jetbrains.compose.resources.stringResource

/**
 * « Calculateurs »: the tank's volume from its sizes, a water change and what it does to a concentration, a product's
 * dose for this tank. Nothing is stored: each result follows what is typed, and the Aquarium's volume is only a start.
 */
@Composable
fun CalculatorsScreen(aquarium: Aquarium, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = currentLocalizedFormatters()
    val unit = aquarium.volumeUnit
    val unitLabel = stringResource(if (unit == VolumeUnit.LITERS) Res.string.calculator_unit_liters else Res.string.calculator_unit_gallons)
    val sizeLabel = stringResource(if (unit == VolumeUnit.LITERS) Res.string.calculator_unit_centimeters else Res.string.calculator_unit_inches)
    val aquariumVolume = formatter.number(aquarium.volume)

    var length by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var changeVolume by rememberSaveable(aquarium.id) { mutableStateOf(aquariumVolume) }
    var changePercent by rememberSaveable { mutableStateOf("") }
    var nitrateNow by rememberSaveable { mutableStateOf("") }
    var nitrateNewWater by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var dosePerVolume by rememberSaveable { mutableStateOf("") }
    var doseVolume by rememberSaveable(aquarium.id) { mutableStateOf(aquariumVolume) }

    fun number(input: String) = parseDecimalInput(input)
    val incomplete = stringResource(Res.string.calculator_incomplete)

    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("calculators-list"),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
        ) {
            item(key = "header") {
                WaterHeader(
                    aquarium = aquarium,
                    title = stringResource(Res.string.calculators_title),
                    subtitle = aquarium.name,
                    leading = {
                        GlassIconButton(
                            AquagendaIcons.ArrowLeft, stringResource(Res.string.back), onClose,
                            Modifier.testTag("calculators-back"),
                        )
                    },
                    modifier = Modifier.overlapNext(30.dp),
                )
            }
            item(key = "sheet-top") { PaperSheetTop() }
            item(key = "volume") {
                val liters = tankVolumeLiters(
                    number(length) ?: 0.0, number(width) ?: 0.0, number(height) ?: 0.0, sizesIn = unit,
                )
                CalculatorCard(
                    title = stringResource(Res.string.calculator_volume_title),
                    result = liters?.let {
                        stringResource(
                            Res.string.calculator_volume_result,
                            formatter.number(it, 1),
                            formatter.number(litersToUsGallons(it), 1),
                        )
                    } ?: incomplete,
                    resultTag = "calculator-volume-result",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(length, { length = it }, stringResource(Res.string.calculator_length, sizeLabel), Modifier.weight(1f))
                        DecimalField(width, { width = it }, stringResource(Res.string.calculator_width, sizeLabel), Modifier.weight(1f))
                    }
                    DecimalField(height, { height = it }, stringResource(Res.string.calculator_height, sizeLabel), Modifier.fillMaxWidth())
                }
            }
            item(key = "water-change") {
                val percent = number(changePercent)
                val toChange = percent?.let { share -> number(changeVolume)?.let { waterChangeVolume(it, share) } }
                val nitrateAfter = percent?.let { share ->
                    number(nitrateNow)?.let { now ->
                        concentrationAfterWaterChange(now, number(nitrateNewWater) ?: 0.0, share)
                    }
                }
                CalculatorCard(
                    title = stringResource(Res.string.calculator_water_change_title),
                    result = toChange?.let { changed ->
                        val volumeLine = stringResource(Res.string.calculator_water_change_result, formatter.number(changed, 1), unitLabel)
                        nitrateAfter?.let {
                            volumeLine + "\n" + stringResource(Res.string.calculator_nitrate_result, formatter.number(it, 1))
                        } ?: volumeLine
                    } ?: incomplete,
                    resultTag = "calculator-water-change-result",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            changeVolume, { changeVolume = it },
                            stringResource(Res.string.calculator_tank_volume, unitLabel), Modifier.weight(1f),
                        )
                        DecimalField(
                            changePercent, { changePercent = it },
                            stringResource(Res.string.calculator_percent), Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            nitrateNow, { nitrateNow = it },
                            stringResource(Res.string.calculator_nitrate_now), Modifier.weight(1f),
                        )
                        DecimalField(
                            nitrateNewWater, { nitrateNewWater = it },
                            stringResource(Res.string.calculator_nitrate_new_water), Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "dose") {
                val amount = number(dose)?.let { labelDose ->
                    number(dosePerVolume)?.let { per -> number(doseVolume)?.let { doseForVolume(labelDose, per, it) } }
                }
                CalculatorCard(
                    title = stringResource(Res.string.calculator_dose_title),
                    result = amount?.let { stringResource(Res.string.calculator_dose_result, formatter.number(it, 2)) } ?: incomplete,
                    resultTag = "calculator-dose-result",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(dose, { dose = it }, stringResource(Res.string.calculator_dose_amount), Modifier.weight(1f))
                        DecimalField(
                            dosePerVolume, { dosePerVolume = it },
                            stringResource(Res.string.calculator_dose_per_volume, unitLabel), Modifier.weight(1f),
                        )
                    }
                    DecimalField(
                        doseVolume, { doseVolume = it },
                        stringResource(Res.string.calculator_tank_volume, unitLabel), Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        StatusBarOverHeader(listState)
    }
}

/** One calculator: its heading, its fields, then its result, announced when it changes. */
@Composable
private fun CalculatorCard(title: String, result: String, resultTag: String, fields: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        GroupLabel(title, Modifier.padding(top = 8.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(colors.surface, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fields()
            Text(
                result,
                modifier = Modifier.padding(top = 4.dp).testTag(resultTag).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    FilledField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
