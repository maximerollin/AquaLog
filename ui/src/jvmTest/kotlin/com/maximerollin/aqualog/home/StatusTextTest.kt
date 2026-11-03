package com.maximerollin.aqualog.home

import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.home_measured_days_ago
import com.maximerollin.aqualog.resources.home_measured_today
import com.maximerollin.aqualog.resources.home_measured_yesterday
import com.maximerollin.aqualog.resources.home_no_measurement_yet
import com.maximerollin.aqualog.resources.home_status_all_in_range
import com.maximerollin.aqualog.resources.home_status_outside_range
import com.maximerollin.aqualog.resources.home_status_with_alert
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTextTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val definition = ParameterDefinition("ph", "a1", BuiltInParameter.PH, true, 0, "", 1, 6.5, 7.5)

    // daysSince reads the system zone, like the home.
    private fun daysAgo(days: Long) = today.minusDays(days).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun overview(measuredDaysAgo: Long?, outside: Int = 0, alerts: Int = 0) = HomeOverview(
        values = listOf(HomeValue(definition, measuredDaysAgo?.let { 7.0 }, measuredDaysAgo?.let(::daysAgo), ReadingLevel.OK, false)),
        outsideRangeCount = outside,
        latestReadingAtEpochMillis = measuredDaysAgo?.let(::daysAgo),
        alertCount = alerts,
    )

    @Test
    fun `an aquarium never measured says so, with no since part`() {
        assertEquals(StatusText(TextRef.Str(Res.string.home_no_measurement_yet), null), statusText(overview(null), today))
    }

    @Test
    fun `all in range measured today`() {
        assertEquals(
            StatusText(TextRef.Str(Res.string.home_status_all_in_range), TextRef.Str(Res.string.home_measured_today)),
            statusText(overview(0), today),
        )
    }

    @Test
    fun `outside range measured yesterday`() {
        assertEquals(
            StatusText(
                TextRef.Plural(Res.plurals.home_status_outside_range, 2, listOf(2)),
                TextRef.Str(Res.string.home_measured_yesterday),
            ),
            statusText(overview(1, outside = 2), today),
        )
    }

    @Test
    fun `with an alert the outside sentence nests in the alert one, measured n days ago`() {
        val outside = TextRef.Plural(Res.plurals.home_status_outside_range, 3, listOf(3))
        assertEquals(
            StatusText(
                TextRef.Plural(Res.plurals.home_status_with_alert, 1, listOf(outside, 1)),
                TextRef.Plural(Res.plurals.home_measured_days_ago, 5, listOf(5)),
            ),
            statusText(overview(5, outside = 3, alerts = 1), today),
        )
    }
}
