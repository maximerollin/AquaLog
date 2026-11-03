package com.maximerollin.aqualog.home

import androidx.compose.runtime.Composable
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.home_measured_days_ago
import com.maximerollin.aqualog.resources.home_measured_today
import com.maximerollin.aqualog.resources.home_measured_yesterday
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.home_status_all_in_range
import com.maximerollin.aqualog.resources.home_status_outside_range
import com.maximerollin.aqualog.resources.home_status_with_alert
import java.time.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A resource and its arguments, resolved by a composable or a suspend caller; an argument may itself be a [TextRef]. */
sealed interface TextRef {
    data class Str(val resource: StringResource, val args: List<Any> = emptyList()) : TextRef
    data class Plural(val resource: PluralStringResource, val quantity: Int, val args: List<Any>) : TextRef
}

@Composable
fun TextRef.resolve(): String {
    val values = args().map { if (it is TextRef) it.resolve() else it }.toTypedArray()
    return when (this) {
        is TextRef.Str -> stringResource(resource, *values)
        is TextRef.Plural -> pluralStringResource(resource, quantity, *values)
    }
}

suspend fun TextRef.load(): String {
    val values = args().map { if (it is TextRef) it.load() else it }.toTypedArray()
    return when (this) {
        is TextRef.Str -> getString(resource, *values)
        is TextRef.Plural -> getPluralString(resource, quantity, *values)
    }
}

private fun TextRef.args() = when (this) {
    is TextRef.Str -> args
    is TextRef.Plural -> args
}

/** The home's status sentence: the summary, then « mesuré … » when there is a Measurement. */
data class StatusText(val summary: TextRef, val since: TextRef?)

/** One selector for the home's StatusLine and the widgets, so they cannot drift. */
fun statusText(overview: HomeOverview, today: LocalDate): StatusText {
    val summary = when {
        !overview.isMeasured -> TextRef.Str(Res.string.home_no_measurement_yet)
        overview.outsideRangeCount > 0 -> TextRef.Plural(
            Res.plurals.home_status_outside_range,
            overview.outsideRangeCount,
            listOf(overview.outsideRangeCount),
        ).let { outside ->
            if (overview.hasAlert) {
                TextRef.Plural(Res.plurals.home_status_with_alert, overview.alertCount, listOf(outside, overview.alertCount))
            } else {
                outside
            }
        }
        else -> TextRef.Str(Res.string.home_status_all_in_range)
    }
    val since = overview.latestReadingAtEpochMillis?.let {
        when (val days = daysSince(it, today)) {
            0 -> TextRef.Str(Res.string.home_measured_today)
            1 -> TextRef.Str(Res.string.home_measured_yesterday)
            else -> TextRef.Plural(Res.plurals.home_measured_days_ago, days, listOf(days))
        }
    }
    return StatusText(summary, since)
}
