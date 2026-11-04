package com.maximerollin.aqualog.widget

import com.maximerollin.aqualog.SEPARATOR
import com.maximerollin.aqualog.dueTestsLabels
import com.maximerollin.aqualog.home.load
import com.maximerollin.aqualog.home.statusText
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.due_tests_row
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.home_status_all_in_range
import com.maximerollin.aqualog.resources.reading_alert_description
import com.maximerollin.aqualog.resources.reading_attention_description
import com.maximerollin.aqualog.resources.widget_nothing_to_do
import com.maximerollin.aqualog.resources.widget_tank_content_description
import com.maximerollin.aqualog.resources.widget_task_overdue
import com.maximerollin.aqualog.resources.widget_task_today
import com.maximerollin.aqualog.resources.widget_value_alert
import com.maximerollin.aqualog.resources.widget_value_attention
import com.maximerollin.aqualog.shortLabelResource
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.shared.toEpochDay
import java.time.LocalDate
import org.jetbrains.compose.resources.getString

data class WidgetTaskRow(val occurrenceId: String, val title: String, val isOverdue: Boolean)

/** A [WidgetAquarium] in words, resolved before Glance composes (Glance cannot read Compose resources). */
data class WidgetTankText(
    val aquariumId: String,
    val name: String,
    /** The Aquarium's photo, read by the widget host (Glance cannot load it from here). */
    val photoId: String? = null,
    val light: WidgetLight,
    /** False for an Aquarium never measured: its dot is the home's outline, not « all in range ». */
    val isMeasured: Boolean = true,
    /** What TalkBack reads for the status dot. */
    val lightDescription: String,
    /** What TalkBack reads for the whole widget: name, state, next. */
    val contentDescription: String,
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
    val lightDescription = getString(
        when {
            // The light is OK for an Aquarium never measured: « Tout est dans la plage » would be false.
            !state.overview.isMeasured -> Res.string.home_no_measurement_yet
            state.light == WidgetLight.ALERT -> Res.string.reading_alert_description
            state.light == WidgetLight.ATTENTION -> Res.string.reading_attention_description
            else -> Res.string.home_status_all_in_range
        },
    )
    return WidgetTankText(
        aquariumId = state.aquarium.id,
        name = state.aquarium.name,
        photoId = state.aquarium.photoId,
        light = state.light,
        isMeasured = state.overview.isMeasured,
        lightDescription = lightDescription,
        contentDescription = getString(Res.string.widget_tank_content_description, state.aquarium.name, lightDescription, next),
        next = next,
        status = statusText(state.overview, today).let { status ->
            status.since?.let { status.summary.load() + SEPARATOR + it.load() } ?: status.summary.load()
        },
        tasks = state.dueTasks.map {
            WidgetTaskRow(it.id, it.title, isOverdue = it.dueDate.toEpochDay() < today.toEpochDay())
        },
    )
}
