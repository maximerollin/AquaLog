package com.maximerollin.aqualog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.Button
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maximerollin.aqualog.AquaLogApplication
import com.maximerollin.aqualog.R
import com.maximerollin.aqualog.TaskCompleter
import com.maximerollin.aqualog.WidgetTarget
import com.maximerollin.aqualog.shared.TaskDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class MediumTankWidget : GlanceAppWidget() {
    // Exact: the content reads the real height to decide how many Tâche rows fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = tankWidgetContent(context, getAppWidgetState<Preferences>(context, id)[PINNED_AQUARIUM_ID])
        provideContent {
            val content = liveWidgetContent(initial) { tankWidgetContent(context, it[PINNED_AQUARIUM_ID]) }
            WidgetTheme { MediumTankContent(content) }
        }
    }
}

class MediumTankWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumTankWidget()
}

/** Ticks a Tâche from the widget with the usual quantity, the way the home does. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val application = context.applicationContext as AquaLogApplication
        val occurrenceId = parameters[OCCURRENCE_ID]
        if (occurrenceId != null) {
            // A failed or stale tick is not reported: the widget below just re-renders from what is stored.
            try {
                val occurrence = application.aquariumRepository.observePendingTaskOccurrences().first()
                    .firstOrNull { it.id == occurrenceId }
                if (occurrence != null) taskCompleter(application).complete(occurrence)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
        updateAllWidgets(context)
    }

    private fun taskCompleter(application: AquaLogApplication) = TaskCompleter(
        repository = application.aquariumRepository,
        scheduler = application.taskReminderScheduler,
        recordEvent = { event -> runCatching { application.productTelemetry.record(event) } },
        now = application.currentTimeMillis,
        today = {
            Instant.ofEpochMilli(application.currentTimeMillis()).atZone(ZoneId.of(application.currentTimeZoneId())).toLocalDate()
                .let { TaskDate(it.year, it.monthValue, it.dayOfMonth) }
        },
        newId = { UUID.randomUUID().toString() },
    )

    companion object {
        val OCCURRENCE_ID = ActionParameters.Key<String>("occurrence-id")
    }
}

@Composable
fun MediumTankContent(content: WidgetContent) {
    when (content) {
        WidgetContent.NoAquarium -> NoAquariumInvitation()
        WidgetContent.NeedsPro -> NeedsProInvitation()
        is WidgetContent.Tank -> MediumTank(content.text)
        // shortcut: the medium widget never loads every Aquarium; this falls back to the first one.
        is WidgetContent.Tanks -> content.rows.firstOrNull()?.let { MediumTank(it) } ?: NoAquariumInvitation()
    }
}

@Composable
private fun MediumTank(text: WidgetTankText) {
    val context = LocalContext.current
    val measure = context.getString(R.string.widget_measure_action)
    val rows = visibleTaskRows(LocalSize.current.height.value, text.tasks.size)
    Column(
        widgetSurface(openAppAction(text.aquariumId, WidgetTarget.HOME))
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().height(ROW_HEIGHT_DP.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            StatusDot(text)
            Button(
                measure,
                onClick = openAppAction(text.aquariumId, WidgetTarget.MEASURE),
                modifier = GlanceModifier.height(ROW_HEIGHT_DP.dp).padding(start = 8.dp).semantics { contentDescription = measure },
            )
        }
        Text(text.status, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
        if (rows == 0) {
            Text(
                text.next,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                maxLines = 2,
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        } else {
            text.tasks.take(rows).forEach { TaskRow(it) }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTaskRow) {
    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.OCCURRENCE_ID to task.occurrenceId)),
            text = task.title,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().semantics { contentDescription = task.title },
        )
        if (task.isOverdue) {
            Text(
                LocalContext.current.getString(R.string.widget_task_overdue_label),
                style = TextStyle(color = GlanceTheme.colors.error, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

private const val MAX_TASKS = 3
private const val ROW_HEIGHT_DP = 48

// Card padding (16) + header row (48) + one status line (~18).
private const val FIXED_HEIGHT_DP = 82

/** How many 48 dp Tâche rows fit under the header in a widget [heightDp] tall: 0 to 3, never more than [taskCount]. */
fun visibleTaskRows(heightDp: Float, taskCount: Int): Int =
    ((heightDp - FIXED_HEIGHT_DP) / ROW_HEIGHT_DP).toInt().coerceIn(0, MAX_TASKS).coerceAtMost(taskCount)
