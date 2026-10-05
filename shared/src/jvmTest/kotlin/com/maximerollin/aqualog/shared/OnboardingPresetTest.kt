package com.maximerollin.aqualog.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPresetTest {
    @Test
    fun `established profile proposes the everyday freshwater Parameters`() {
        val active = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED)
            .filter(ParameterDefinitionDraft::isActive)
            .map(ParameterDefinitionDraft::parameter)

        assertEquals(
            listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.NITRITE,
                BuiltInParameter.NITRATE,
                BuiltInParameter.GH,
                BuiltInParameter.KH,
            ),
            active,
        )
    }

    @Test
    fun `cycling profile prioritizes nitrogen cycle Parameters and keeps every built-in editable`() {
        val parameters = OnboardingPresets.parametersFor(AquariumProfile.CYCLING)

        assertEquals(BuiltInParameter.entries.size, parameters.size)
        assertEquals(
            listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.AMMONIA,
                BuiltInParameter.NITRITE,
                BuiltInParameter.NITRATE,
            ),
            parameters.filter(ParameterDefinitionDraft::isActive).map(ParameterDefinitionDraft::parameter),
        )
        assertEquals(parameters.indices.toList(), parameters.map(ParameterDefinitionDraft::position))

        val ammonia = parameters.single { it.parameter == BuiltInParameter.AMMONIA }
        assertEquals("mg/L", ammonia.unit)
        assertEquals(2, ammonia.precision)
        assertEquals(0.0, ammonia.indicativeMinimum)
        assertEquals(0.0, ammonia.indicativeMaximum)

        assertTrue(parameters.single { it.parameter == BuiltInParameter.NITRITE }.isActive)
        assertFalse(parameters.single { it.parameter == BuiltInParameter.SALINITY }.isActive)
    }

    @Test
    fun `planted shrimp profile proposes hardness TDS nitrate and phosphate`() {
        val active = OnboardingPresets.parametersFor(AquariumProfile.PLANTED_SHRIMP)
            .filter(ParameterDefinitionDraft::isActive)
            .map(ParameterDefinitionDraft::parameter)

        assertEquals(
            listOf(
                BuiltInParameter.TEMPERATURE,
                BuiltInParameter.PH,
                BuiltInParameter.GH,
                BuiltInParameter.KH,
                BuiltInParameter.TDS,
                BuiltInParameter.NITRATE,
                BuiltInParameter.PHOSPHATE,
            ),
            active,
        )
    }
}
