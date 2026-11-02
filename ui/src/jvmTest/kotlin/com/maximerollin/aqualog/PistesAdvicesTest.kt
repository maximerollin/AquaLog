package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.assistance.PisteKey
import com.maximerollin.aqualog.shared.assistance.ReadingSample
import kotlin.test.Test
import kotlin.test.assertEquals

class PistesAdvicesTest {
    private val day = TaskDate(2026, 11, 2)

    private fun definition(parameter: BuiltInParameter, position: Int, min: Double?, max: Double?) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
        position = position, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
    private val nitrite = definition(BuiltInParameter.NITRITE, 1, 0.0, 0.0)
    private val nitrate = definition(BuiltInParameter.NITRATE, 2, 0.0, 40.0)
    private val all = listOf(nitrate, nitrite, ph)

    private fun readings(vararg values: Pair<ParameterDefinition, Double>) = values.map { (definition, value) -> ReadingSample(definition.id, value, day) }

    @Test
    fun `only out of range values give advice, alert before attention then position`() {
        val advices = pistesAdvices(
            all,
            readings(ph to 7.0, nitrite to 0.25, nitrate to 90.0), // nitrate beyond its alert threshold
            AquariumProfile.ESTABLISHED,
            waterChangeOverdue = false,
        )
        assertEquals(listOf(BuiltInParameter.NITRATE, BuiltInParameter.NITRITE), advices.map { it.advice.parameter })
    }

    @Test
    fun `while cycling nitrite is not an alert, so it keeps its position`() {
        val advices = pistesAdvices(all, readings(ph to 8.6, nitrite to 2.0, nitrate to 90.0), AquariumProfile.CYCLING, false)
        assertEquals(listOf(BuiltInParameter.PH, BuiltInParameter.NITRATE, BuiltInParameter.NITRITE), advices.map { it.advice.parameter })
    }

    @Test
    fun `the retest is dated from the reading day`() {
        val dated = pistesAdvices(all, readings(nitrate to 60.0), AquariumProfile.ESTABLISHED, false).single()
        assertEquals(TaskDate(2026, 11, 5), dated.retestOn)
    }

    @Test
    fun `nothing out of range gives no advice`() {
        assertEquals(emptyList(), pistesAdvices(all, readings(ph to 7.0, nitrite to 0.0), AquariumProfile.ESTABLISHED, false))
    }

    @Test
    fun `an overdue water change leads the pistes`() {
        val advice = pistesAdvices(all, readings(nitrite to 0.25), AquariumProfile.ESTABLISHED, waterChangeOverdue = true).single()
        assertEquals(PisteKey.WATER_CHANGE_OVERDUE, advice.advice.pistes.first())
    }
}
