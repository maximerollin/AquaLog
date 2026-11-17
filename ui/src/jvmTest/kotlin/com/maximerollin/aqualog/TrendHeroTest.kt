package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendPeriod
import com.maximerollin.aqualog.shared.TrendPoint
import com.maximerollin.aqualog.shared.TrendSnapshot
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendHeroTest {
    private fun definition(parameter: BuiltInParameter, position: Int, min: Double? = null, max: Double? = null) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
        position = position, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private fun series(definition: ParameterDefinition, vararg values: Double) = ParameterTrendSeries(
        definition,
        values.mapIndexed { index, value -> TrendPoint("s$index", index * 86_400_000L, value) },
    )

    private val day = 86_400_000L

    private fun snapshot(series: List<ParameterTrendSeries>, days: Int = 30) = TrendSnapshot(
        aquarium = Aquarium("a1", "Tank", 100.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED),
        period = TrendPeriod.THIRTY_DAYS,
        windowStartEpochMillis = 0L,
        windowEndEpochMillis = days * day,
        series = series,
        events = emptyList(),
    )

    private val temperature = definition(BuiltInParameter.TEMPERATURE, 0, 24.0, 27.0)
    private val ph = definition(BuiltInParameter.PH, 1, 6.5, 7.5)
    private val nitrate = definition(BuiltInParameter.NITRATE, 3, max = 40.0)

    @Test
    fun `hero is the first parameter to watch by position`() {
        // Given out of order: position, not list order, decides.
        val snapshot = snapshot(listOf(series(nitrate, 60.0), series(ph, 7.8), series(temperature, 25.0)))
        assertEquals(ph, heroSeries(snapshot)?.definition)
    }

    @Test
    fun `hero falls back to the first measured parameter`() {
        val snapshot = snapshot(listOf(series(temperature), series(ph, 7.0)))
        assertEquals(ph, heroSeries(snapshot)?.definition)
    }

    @Test
    fun `no hero without any measurement`() {
        assertNull(heroSeries(snapshot(listOf(series(temperature), series(ph)))))
    }

    @Test
    fun `change needs two points`() {
        assertEquals(0.7, periodChange(series(ph, 7.1, 7.8))!!, 1e-9)
        assertNull(periodChange(series(ph, 7.8)))
    }

    @Test
    fun `window days of a 30 day snapshot`() {
        assertEquals(30, windowDays(snapshot(emptyList(), 30)))
    }
}
