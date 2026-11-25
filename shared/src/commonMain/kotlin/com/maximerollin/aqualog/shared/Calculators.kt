package com.maximerollin.aqualog.shared

/** Litres in one US gallon. */
const val LITERS_PER_US_GALLON = 3.785411784

/** Cubic centimetres in one cubic inch. */
private const val CUBIC_CENTIMETERS_PER_CUBIC_INCH = 16.387064

/**
 * The volume of a rectangular tank from its inner sizes, in litres: centimetres for [VolumeUnit.LITERS], inches
 * for [VolumeUnit.US_GALLONS]. Null unless the three sizes are positive.
 */
fun tankVolumeLiters(length: Double, width: Double, height: Double, sizesIn: VolumeUnit): Double? {
    if (listOf(length, width, height).any { !it.isFinite() || it <= 0.0 }) return null
    val cubicCentimeters = length * width * height *
        if (sizesIn == VolumeUnit.US_GALLONS) CUBIC_CENTIMETERS_PER_CUBIC_INCH else 1.0
    return cubicCentimeters / 1_000.0
}

fun litersToUsGallons(liters: Double): Double = liters / LITERS_PER_US_GALLON

/** The water to take out and put back for a change of [percent] of [volume], in the unit of [volume]. */
fun waterChangeVolume(volume: Double, percent: Double): Double? =
    if (volume.isFinite() && volume > 0.0 && percent.isFinite() && percent in 0.0..100.0) volume * percent / 100.0 else null

/**
 * A concentration after a water change of [percent]: what stays keeps [current], what comes in brings [newWater].
 * An estimate: it ignores what the tank produces meanwhile.
 */
fun concentrationAfterWaterChange(current: Double, newWater: Double, percent: Double): Double? {
    if (listOf(current, newWater, percent).any { !it.isFinite() } || current < 0.0 || newWater < 0.0 || percent !in 0.0..100.0) {
        return null
    }
    return current * (1.0 - percent / 100.0) + newWater * percent / 100.0
}

/** A label's « [dose] for [perVolume] » scaled to [volume]; the two volumes are in the same unit. */
fun doseForVolume(dose: Double, perVolume: Double, volume: Double): Double? =
    if (listOf(dose, perVolume, volume).all { it.isFinite() } && dose >= 0.0 && perVolume > 0.0 && volume > 0.0) {
        dose * volume / perVolume
    } else {
        null
    }
