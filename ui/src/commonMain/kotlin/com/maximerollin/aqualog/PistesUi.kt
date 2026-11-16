package com.maximerollin.aqualog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.assistance.PisteAdvice
import com.maximerollin.aqualog.shared.assistance.PisteKey
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import com.maximerollin.aqualog.shared.assistance.direction
import com.maximerollin.aqualog.shared.assistance.pistesFor
import com.maximerollin.aqualog.shared.assistance.readingLevel
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.toEpochDay
import com.maximerollin.aqualog.ui.theme.CarnetSectionTitle
import org.jetbrains.compose.resources.stringResource

/** An advice and the day to test again: the reading's day plus the pistes' delay. */
data class DatedPisteAdvice(val advice: PisteAdvice, val retestOn: TaskDate)

/** One advice per out-of-range reading, ALERT before ATTENTION, then by parameter position. */
fun pistesAdvices(
    definitions: List<ParameterDefinition>,
    readings: List<ReadingSample>,
    profile: AquariumProfile,
    waterChangeOverdue: Boolean,
): List<DatedPisteAdvice> {
    val byDefinition = readings.associateBy { it.parameterDefinitionId }
    return definitions.filter { it.isActive }.sortedBy { it.position }
        .mapNotNull { definition ->
            val reading = byDefinition[definition.id] ?: return@mapNotNull null
            val direction = direction(definition, reading.value) ?: return@mapNotNull null
            val advice = pistesFor(definition.parameter, direction, profile, waterChangeOverdue)
            (if (readingLevel(definition, reading.value, profile) == ReadingLevel.ALERT) 0 else 1) to
                DatedPisteAdvice(advice, reading.day.plusDays(advice.retestInDays))
        }
        .sortedBy { it.first } // stable: keeps parameter position within a level
        .map { it.second }
}

/** The advice blocks followed by one disclaimer; plain readable text, no hidden semantics. */
@Composable
fun PistesContent(advices: List<DatedPisteAdvice>, today: TaskDate, modifier: Modifier = Modifier) {
    val formatter = currentLocalizedFormatters()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        advices.forEach { (advice, retestOn) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CarnetSectionTitle(stringResource(Res.string.pistes_title, advice.parameter.label()))
                advice.pistes.forEach { Text(it.text(), style = MaterialTheme.typography.bodyMedium) }
                val days = retestOn.toEpochDay() - today.toEpochDay()
                val retest = when {
                    days < 0 -> stringResource(Res.string.pistes_retest_asap)
                    days == 0L -> stringResource(Res.string.pistes_retest_today)
                    days == 1L -> stringResource(Res.string.pistes_retest_tomorrow)
                    else -> stringResource(Res.string.pistes_retest_on_date, formatter.date(retestOn.year, retestOn.month, retestOn.day))
                }
                Text(
                    stringResource(Res.string.pistes_retest, retest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(Res.string.pistes_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown right after saving a Session with new out-of-range values, and from the home's status card; never blocks anything. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PistesSheet(advices: List<DatedPisteAdvice>, today: TaskDate, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PistesContent(advices, today)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.close))
            }
        }
    }
}

@Composable
private fun PisteKey.text(): String = stringResource(
    when (this) {
        PisteKey.WATER_CHANGE_30 -> Res.string.piste_water_change_30
        PisteKey.WATER_CHANGE_30_TO_50 -> Res.string.piste_water_change_30_to_50
        PisteKey.WATER_CHANGE_PARTIAL -> Res.string.piste_water_change_partial
        PisteKey.WATER_CHANGE_SOFTER_WATER -> Res.string.piste_water_change_softer_water
        PisteKey.WATER_CHANGE_PURER_WATER -> Res.string.piste_water_change_purer_water
        PisteKey.CUT_WITH_OSMOSIS -> Res.string.piste_cut_with_osmosis
        PisteKey.WATER_CHANGE_OVERDUE -> Res.string.piste_water_change_overdue
        PisteKey.FEED_LESS_2_DAYS -> Res.string.piste_feed_less_2_days
        PisteKey.ADD_STARTER_BACTERIA -> Res.string.piste_add_starter_bacteria
        PisteKey.ADD_AMMONIA_DETOXIFIER -> Res.string.piste_add_ammonia_detoxifier
        PisteKey.CHECK_FOOD_AMOUNT -> Res.string.piste_check_food_amount
        PisteKey.ADD_FAST_GROWING_PLANTS -> Res.string.piste_add_fast_growing_plants
        PisteKey.CHECK_KH_LOW_DROPS_PH -> Res.string.piste_check_kh_low_drops_ph
        PisteKey.ADD_KH_BUFFER -> Res.string.piste_add_kh_buffer
        PisteKey.CHECK_TAP_WATER_AND_DECOR -> Res.string.piste_check_tap_water_and_decor
        PisteKey.CHECK_HEATER_SETTING -> Res.string.piste_check_heater_setting
        PisteKey.VENT_OR_LIGHTS_OFF -> Res.string.piste_vent_or_lights_off
        PisteKey.CHECK_HEATER_WORKS -> Res.string.piste_check_heater_works
        PisteKey.ADJUST_HEATER_POWER -> Res.string.piste_adjust_heater_power
        PisteKey.ADD_REMINERALIZER -> Res.string.piste_add_remineralizer
        PisteKey.REDUCE_FOOD -> Res.string.piste_reduce_food
        PisteKey.ADD_ANTI_PHOSPHATE -> Res.string.piste_add_anti_phosphate
        PisteKey.RETEST_TO_CONFIRM -> Res.string.piste_retest_to_confirm
        PisteKey.CYCLING_NORMAL -> Res.string.piste_cycling_normal
    },
)
