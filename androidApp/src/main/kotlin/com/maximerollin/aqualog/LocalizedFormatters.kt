package com.maximerollin.aqualog

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.maximerollin.aqualog.shared.VolumeUnit
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class LocalizedFormatters(
    private val locale: Locale,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun volume(value: Double, unit: VolumeUnit): String =
        "${number(value)} ${if (unit == VolumeUnit.LITERS) "L" else "US gal"}"

    fun number(value: Double, maximumFractionDigits: Int = 3): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits
            isGroupingUsed = false
        }.format(value)

    fun dateTime(epochMillis: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT, locale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(Date(epochMillis))

    fun shortDateTime(epochMillis: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.SHORT, DateFormat.SHORT, locale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(Date(epochMillis))

    fun time(minuteOfDay: Int): String = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
        timeZone = this@LocalizedFormatters.timeZone
    }.format(Date(minuteOfDay * 60_000L))

    fun date(year: Int, month: Int, day: Int): String = DateFormat.getDateInstance(
        DateFormat.MEDIUM,
        locale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(
        GregorianCalendar(timeZone, locale).apply {
            clear()
            set(year, month - 1, day)
        }.time,
    )
}

@Composable
fun currentLocalizedFormatters(): LocalizedFormatters {
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) configuration.locales[0] else configuration.locale
    return LocalizedFormatters(locale)
}
