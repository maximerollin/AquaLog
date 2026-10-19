package com.maximerollin.aqualog

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.AnnualProOffer
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class LocalizedFormatters(
    private val formatLocale: Locale,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun volume(value: Double, unit: VolumeUnit): String =
        "${number(value)} ${if (unit == VolumeUnit.LITERS) "L" else "US gal"}"

    fun number(value: Double, maximumFractionDigits: Int = 3): String =
        NumberFormat.getNumberInstance(formatLocale).apply {
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits
            isGroupingUsed = false
        }.format(value)

    fun dateTime(epochMillis: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT, formatLocale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(Date(epochMillis))

    fun shortDateTime(epochMillis: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.SHORT, DateFormat.SHORT, formatLocale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(Date(epochMillis))

    fun date(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, formatLocale).apply {
        timeZone = this@LocalizedFormatters.timeZone
    }.format(Date(epochMillis))

    fun time(minuteOfDay: Int, referenceEpochMillis: Long = System.currentTimeMillis()): String = DateFormat.getTimeInstance(DateFormat.SHORT, formatLocale).apply {
        timeZone = this@LocalizedFormatters.timeZone
    }.format(GregorianCalendar(timeZone, formatLocale).apply {
        timeInMillis = referenceEpochMillis
        set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(java.util.Calendar.MINUTE, minuteOfDay % 60)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.time)

    fun date(year: Int, month: Int, day: Int): String = DateFormat.getDateInstance(
        DateFormat.MEDIUM,
        formatLocale,
    ).apply { timeZone = this@LocalizedFormatters.timeZone }.format(
        GregorianCalendar(timeZone, formatLocale).apply {
            clear()
            set(year, month - 1, day)
        }.time,
    )
}

internal fun AnnualProOffer.localizedBillingDate(formatter: LocalizedFormatters): String =
    formatter.date(billingDateEpochMillis)

@Composable
@SuppressLint("NonObservableLocale")
fun currentLocalizedFormatters(): LocalizedFormatters {
    val configuration = LocalConfiguration.current
    return remember(configuration) { LocalizedFormatters(regionalFormatLocale()) }
}

private fun regionalFormatLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Locale.getDefault(Locale.Category.FORMAT)
} else {
    Locale.getDefault()
}
