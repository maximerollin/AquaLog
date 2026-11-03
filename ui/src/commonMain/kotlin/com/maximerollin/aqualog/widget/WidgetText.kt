package com.maximerollin.aqualog.widget

import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.dueTestsLabels
import com.maximerollin.aqualog.home.daysSince
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.due_tests_row
import com.maximerollin.aqualog.resources.home_measured_days_ago
import com.maximerollin.aqualog.resources.home_measured_today
import com.maximerollin.aqualog.resources.home_measured_yesterday
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.home_status_all_in_range
import com.maximerollin.aqualog.resources.home_status_outside_range
import com.maximerollin.aqualog.resources.home_status_with_alert
import com.maximerollin.aqualog.resources.reading_alert_description
import com.maximerollin.aqualog.resources.reading_attention_description
import com.maximerollin.aqualog.resources.widget_nothing_to_do
import com.maximerollin.aqualog.resources.widget_task_overdue
import com.maximerollin.aqualog.resources.widget_task_today
import com.maximerollin.aqualog.resources.widget_value_alert
import com.maximerollin.aqualog.resources.widget_value_attention
import com.maximerollin.aqualog.shortLabelResource
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.toEpochDay
import java.time.LocalDate
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

data class WidgetTaskRow(val occurrenceId: String, val title: String, val isOverdue: Boolean)

/** A [WidgetAquarium] in words, resolved before Glance composes (Glance cannot read Compose resources). */
data class WidgetTankText(
    val aquariumId: String,
    val name: String,
    val light: WidgetLight,
    /** What TalkBack reads for the status dot. */
    val lightDescription: String,
    val next: String,
    val status: String,
    val tasks: List<WidgetTaskRow>,
)

suspend fun widgetText(state: WidgetAquarium, today: LocalDate): WidgetTankText {
    val next = when (val step = state.next) {
        is NextStep.Value -> getString(
            if (step.level == ReadingLevel.ALERT) Res.string.widget_value_alert else Res.string.widget_value_attention,
            getString(step.definition.parameter.shortLabelResource()),
        )
        is NextStep.Task -> getString(
            if (step.isOverdue) Res.string.widget_task_overdue else Res.string.widget_task_today,
            step.occurrence.title,
        )
        is NextStep.Tests -> getString(Res.string.due_tests_row, dueTestsLabels(step.tests))
        NextStep.Nothing -> getString(Res.string.widget_nothing_to_do)
    }
    return WidgetTankText(
        aquariumId = state.aquarium.id,
        name = state.aquarium.name,
        light = state.light,
        lightDescription = getString(
            when (state.light) {
                WidgetLight.OK -> Res.string.home_status_all_in_range
                WidgetLight.ATTENTION -> Res.string.reading_attention_description
                WidgetLight.ALERT -> Res.string.reading_alert_description
            },
        ),
        next = next,
        status = statusLine(state, today),
        tasks = state.dueTasks.map {
            WidgetTaskRow(it.id, it.title, isOverdue = it.dueDate.toEpochDay() < today.toEpochDay())
        },
    )
}

/** The home's StatusLine sentence, with the same keys. */
private suspend fun statusLine(state: WidgetAquarium, today: LocalDate): String {
    val overview = state.overview
    val summary = when {
        overview.values.none { it.value != null } -> getString(Res.string.home_no_measurement_yet)
        overview.outsideRangeCount > 0 -> getPluralString(
            Res.plurals.home_status_outside_range,
            overview.outsideRangeCount,
            overview.outsideRangeCount,
        ).let { outside ->
            if (overview.hasAlert) {
                getPluralString(Res.plurals.home_status_with_alert, overview.alertCount, outside, overview.alertCount)
            } else {
                outside
            }
        }
        else -> getString(Res.string.home_status_all_in_range)
    }
    val since = overview.latestReadingAtEpochMillis?.let {
        when (val days = daysSince(it, today)) {
            0 -> getString(Res.string.home_measured_today)
            1 -> getString(Res.string.home_measured_yesterday)
            else -> getPluralString(Res.plurals.home_measured_days_ago, days, days)
        }
    }
    return if (since == null) summary else summary + SEPARATOR + since
}
