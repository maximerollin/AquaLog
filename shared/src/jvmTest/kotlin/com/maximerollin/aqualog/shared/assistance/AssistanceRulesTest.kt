package com.maximerollin.aqualog.shared.assistance

import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssistanceRulesTest {
    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun definition(
        parameter: BuiltInParameter,
        nullRange: Boolean = false,
    ): ParameterDefinition {
        val draft = OnboardingPresets.parametersFor(com.maximerollin.aqualog.shared.AquariumProfile.ESTABLISHED)
            .first { it.parameter == parameter }
        return ParameterDefinition(
            id = "def-${parameter.storageValue}",
            aquariumId = "aq",
            parameter = parameter,
            isActive = true,
            position = draft.position,
            unit = draft.unit,
            precision = draft.precision,
            indicativeMinimum = if (nullRange) null else draft.indicativeMinimum,
            indicativeMaximum = if (nullRange) null else draft.indicativeMaximum,
        )
    }

    private fun level(parameter: BuiltInParameter, value: Double, nullRange: Boolean = false) =
        readingLevel(definition(parameter, nullRange), value)

    private fun check(
        parameter: BuiltInParameter,
        value: Double,
        previous: Double? = null,
        previousDaysAgo: Int? = null,
        nullRange: Boolean = false,
    ) = checkMeasurement(
        definition(parameter, nullRange),
        value,
        previous,
        previousDaysAgo?.let { now - it * day },
        now,
    )

    @Test
    fun `pH 7_2 is OK, 7_8 ATTENTION, 8_6 ALERT`() {
        assertEquals(ReadingLevel.OK, level(BuiltInParameter.PH, 7.2))
        assertEquals(ReadingLevel.ATTENTION, level(BuiltInParameter.PH, 7.8))
        assertEquals(ReadingLevel.ALERT, level(BuiltInParameter.PH, 8.6))
    }

    @Test
    fun `nitrite 0_25 ATTENTION, 0_5 ALERT`() {
        assertEquals(ReadingLevel.ATTENTION, level(BuiltInParameter.NITRITE, 0.25))
        assertEquals(ReadingLevel.ALERT, level(BuiltInParameter.NITRITE, 0.5))
    }

    @Test
    fun `temperature without alert threshold side stays ATTENTION`() {
        assertEquals(ReadingLevel.ATTENTION, level(BuiltInParameter.TEMPERATURE, 29.0))
    }

    @Test
    fun `TDS 900 with null range is ALERT`() {
        assertEquals(ReadingLevel.ALERT, level(BuiltInParameter.TDS, 900.0, nullRange = true))
    }

    @Test
    fun `pH 72 is implausible with suggestion 7_2`() {
        assertEquals(MeasurementConcern.Implausible(7.2), check(BuiltInParameter.PH, 72.0))
    }

    @Test
    fun `pH 9 is plausible`() {
        assertNull(check(BuiltInParameter.PH, 9.0))
    }

    @Test
    fun `nitrate 2000 is implausible with suggestion 20`() {
        assertEquals(MeasurementConcern.Implausible(20.0), check(BuiltInParameter.NITRATE, 2000.0))
    }

    @Test
    fun `temperature 250 suggests 25_0`() {
        assertEquals(MeasurementConcern.Implausible(25.0), check(BuiltInParameter.TEMPERATURE, 250.0))
    }

    @Test
    fun `with no indicative range a plausible divided value is suggested`() {
        assertEquals(MeasurementConcern.Implausible(3000.0), check(BuiltInParameter.TDS, 30000.0, nullRange = true))
    }

    @Test
    fun `implausible without a fitting divided value has no suggestion`() {
        assertEquals(MeasurementConcern.Implausible(null), check(BuiltInParameter.PH, 5000.0))
    }

    @Test
    fun `nitrate 80 after 20 three days ago is a big jump`() {
        assertEquals(
            MeasurementConcern.BigJump(20.0, now - 3 * day),
            check(BuiltInParameter.NITRATE, 80.0, previous = 20.0, previousDaysAgo = 3),
        )
    }

    @Test
    fun `nitrate 80 after 20 twenty days ago is not`() {
        assertNull(check(BuiltInParameter.NITRATE, 80.0, previous = 20.0, previousDaysAgo = 20))
    }

    @Test
    fun `implausible beats big jump`() {
        assertEquals(
            MeasurementConcern.Implausible(20.0),
            check(BuiltInParameter.NITRATE, 2000.0, previous = 20.0, previousDaysAgo = 1),
        )
    }
}
