package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.assistance.PisteKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PistesAdvicesTest {
    private fun definition(parameter: BuiltInParameter, position: Int, min: Double?, max: Double?) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
        position = position, unit = "", precision = 1, indicativeMinimum = min, indicativeMaximum = max,
    )

    private val ph = definition(BuiltInParameter.PH, 0, 6.5, 7.5)
    private val nitrite = definition(BuiltInParameter.NITRITE, 1, 0.0, 0.0)
    private val nitrate = definition(BuiltInParameter.NITRATE, 2, 0.0, 40.0)
    private val all = listOf(nitrate, nitrite, ph)

    @Test
    fun `only out of range values give advice, alert before attention then position`() {
        val advices = pistesAdvices(
            all,
            mapOf(ph.id to 7.0, nitrite.id to 0.25, nitrate.id to 90.0), // nitrate beyond its alert threshold
            AquariumProfile.ESTABLISHED,
            waterChangeOverdue = false,
        )
        assertEquals(listOf(BuiltInParameter.NITRATE, BuiltInParameter.NITRITE), advices.map { it.parameter })
    }

    @Test
    fun `nothing out of range gives no advice`() {
        assertEquals(emptyList(), pistesAdvices(all, mapOf(ph.id to 7.0, nitrite.id to 0.0), AquariumProfile.ESTABLISHED, false))
    }

    @Test
    fun `an overdue water change leads the pistes`() {
        val advice = pistesAdvices(all, mapOf(nitrite.id to 0.25), AquariumProfile.ESTABLISHED, waterChangeOverdue = true).single()
        assertEquals(PisteKey.WATER_CHANGE_OVERDUE, advice.pistes.first())
    }
}
