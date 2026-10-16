package com.maximerollin.aqualog

import java.text.NumberFormat
import java.util.Locale

enum class VolumeDisplayUnit { LITERS, US_GALLONS }

class LocalizedFormatters(private val locale: Locale) {
    fun volume(value: Double, unit: VolumeDisplayUnit): String =
        "${number(value)} ${if (unit == VolumeDisplayUnit.LITERS) "L" else "US gal"}"

    fun number(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
        isGroupingUsed = false
    }.format(value)

    fun parseDecimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
}
