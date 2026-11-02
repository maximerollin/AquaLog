package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class PistesTest {
    private fun definition(
        parameter: BuiltInParameter,
        minimum: Double?,
        maximum: Double?,
    ) = ParameterDefinition(
        id = "def-${parameter.storageValue}",
        aquariumId = "aq",
        parameter = parameter,
        isActive = true,
        position = 0,
        unit = "",
        precision = 1,
        indicativeMinimum = minimum,
        indicativeMaximum = maximum,
    )

    @Test
    fun `direction is null in range, HIGH above the maximum or at the alert threshold, LOW below the minimum or at the alert threshold`() {
        val ph = definition(BuiltInParameter.PH, minimum = 6.5, maximum = 9.0)
        assertEquals(null, direction(ph, 7.0))
        assertEquals(Direction.HIGH, direction(ph, 9.5))
        assertEquals(Direction.HIGH, direction(ph, 8.5))
        assertEquals(Direction.LOW, direction(ph, 6.2))
        assertEquals(Direction.LOW, direction(ph, 6.0))
    }

    @Test
    fun `nitrite high gives the three pistes and retest 1`() {
        val advice = pistesFor(BuiltInParameter.NITRITE, Direction.HIGH, AquariumProfile.ESTABLISHED, waterChangeOverdue = false)
        assertEquals(
            listOf(PisteKey.WATER_CHANGE_30, PisteKey.FEED_LESS_2_DAYS, PisteKey.ADD_STARTER_BACTERIA),
            advice.pistes,
        )
        assertEquals(1, advice.retestInDays)
    }

    @Test
    fun `nitrate high retest 3`() {
        val advice = pistesFor(BuiltInParameter.NITRATE, Direction.HIGH, AquariumProfile.ESTABLISHED, waterChangeOverdue = false)
        assertEquals(3, advice.retestInDays)
    }

    @Test
    fun `ph low starts with KH`() {
        val advice = pistesFor(BuiltInParameter.PH, Direction.LOW, AquariumProfile.ESTABLISHED, waterChangeOverdue = false)
        assertEquals(PisteKey.CHECK_KH_LOW_DROPS_PH, advice.pistes.first())
    }

    @Test
    fun `overdue water change comes first and keeps 3 pistes`() {
        val advice = pistesFor(BuiltInParameter.NITRITE, Direction.HIGH, AquariumProfile.ESTABLISHED, waterChangeOverdue = true)
        assertEquals(
            listOf(PisteKey.WATER_CHANGE_OVERDUE, PisteKey.FEED_LESS_2_DAYS, PisteKey.ADD_STARTER_BACTERIA),
            advice.pistes,
        )
        assertEquals(1, advice.retestInDays)
    }

    @Test
    fun `cycling nitrite high gives the cycling note, retest 2`() {
        val advice = pistesFor(BuiltInParameter.NITRITE, Direction.HIGH, AquariumProfile.CYCLING, waterChangeOverdue = false)
        assertEquals(listOf(PisteKey.CYCLING_NORMAL), advice.pistes)
        assertEquals(2, advice.retestInDays)
    }

    @Test
    fun `gh high with overdue water change keeps the osmosis piste`() {
        val advice = pistesFor(BuiltInParameter.GH, Direction.HIGH, AquariumProfile.ESTABLISHED, waterChangeOverdue = true)
        assertEquals(listOf(PisteKey.WATER_CHANGE_OVERDUE, PisteKey.CUT_WITH_OSMOSIS), advice.pistes)
    }

    @Test
    fun `cycling ammonia high with overdue water change still gives the cycling note`() {
        val advice = pistesFor(BuiltInParameter.AMMONIA, Direction.HIGH, AquariumProfile.CYCLING, waterChangeOverdue = true)
        assertEquals(listOf(PisteKey.CYCLING_NORMAL), advice.pistes)
        assertEquals(2, advice.retestInDays)
    }

    @Test
    fun `unknown combination gives the generic pistes`() {
        val advice = pistesFor(BuiltInParameter.NITRATE, Direction.LOW, AquariumProfile.ESTABLISHED, waterChangeOverdue = false)
        assertEquals(listOf(PisteKey.RETEST_TO_CONFIRM, PisteKey.WATER_CHANGE_PARTIAL), advice.pistes)
        assertEquals(2, advice.retestInDays)
    }
}
