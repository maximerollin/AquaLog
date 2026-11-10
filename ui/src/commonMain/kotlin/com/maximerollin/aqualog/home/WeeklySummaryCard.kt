package com.maximerollin.aqualog.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.currentLocalizedFormatters
import com.maximerollin.aqualog.joinLabels
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.list_pair
import com.maximerollin.aqualog.resources.series_weeks
import com.maximerollin.aqualog.resources.weekly_summary_change
import com.maximerollin.aqualog.resources.weekly_summary_falling
import com.maximerollin.aqualog.resources.weekly_summary_next_week
import com.maximerollin.aqualog.resources.weekly_summary_pro_invite
import com.maximerollin.aqualog.resources.weekly_summary_rising
import com.maximerollin.aqualog.resources.weekly_summary_tasks
import com.maximerollin.aqualog.resources.weekly_summary_tests
import com.maximerollin.aqualog.resources.weekly_summary_title
import com.maximerollin.aqualog.shortLabelResource
import com.maximerollin.aqualog.shared.assistance.WeeklySummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** « Bilan de la semaine »: counts and series for everyone; value changes and next week's tests for Pro only. */
@Composable
internal fun WeeklySummaryCard(summary: WeeklySummary, isPro: Boolean, onOpenPro: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = currentLocalizedFormatters()
    val body = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onBackground
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.weekly_summary_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            listOfNotNull(
                pluralStringResource(Res.plurals.weekly_summary_tests, summary.testsDone, summary.testsDone),
                pluralStringResource(Res.plurals.weekly_summary_tasks, summary.tasksCompleted, summary.tasksCompleted),
                summary.series.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.series_weeks, it, it) },
            ).joinToString(" · "),
            style = body,
            color = color,
        )
        if (!isPro) {
            TextButton(onClick = onOpenPro, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.weekly_summary_pro_invite))
            }
            return@Column
        }
        summary.changes.forEach { change ->
            val precision = change.definition.precision
            Text(
                stringResource(
                    Res.string.weekly_summary_change,
                    stringResource(change.definition.parameter.shortLabelResource()),
                    formatter.number(change.from, precision),
                    formatter.number(change.to, precision),
                    stringResource(if (change.to > change.from) Res.string.weekly_summary_rising else Res.string.weekly_summary_falling),
                ),
                style = body,
                color = color,
            )
        }
        if (summary.nextWeek.isNotEmpty()) {
            val labels = joinLabels(summary.nextWeek.map { stringResource(it.definition.parameter.shortLabelResource()) }) { first, last ->
                stringResource(Res.string.list_pair, first, last)
            }
            Text(stringResource(Res.string.weekly_summary_next_week, labels), style = body, color = color)
        }
    }
}
