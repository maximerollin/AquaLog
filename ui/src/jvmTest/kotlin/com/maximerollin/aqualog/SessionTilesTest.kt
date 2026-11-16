package com.maximerollin.aqualog

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTilesTest {
    private fun definition(parameter: BuiltInParameter, min: Double? = null, max: Double? = null) = ParameterDefinition(
        id = "d-${parameter.storageValue}", aquariumId = "a1", parameter = parameter, isActive = true,
        position = 0, unit = OnboardingPresets.presetUnit(parameter), precision = 1,
        indicativeMinimum = min, indicativeMaximum = max,
    )

    private val established = AquariumProfile.ESTABLISHED

    @Test
    fun `blank is empty and garbage is invalid`() {
        val ph = definition(BuiltInParameter.PH, 6.5, 7.5)
        assertEquals(SessionTileState.EMPTY, sessionTileState("", ph, established))
        assertEquals(SessionTileState.INVALID, sessionTileState("7,8,1", ph, established))
    }

    @Test
    fun `ph 7_8 over 6_5-7_5 is above, 6_2 is below, 6_0 far below, 7_0 in range`() {
        val ph = definition(BuiltInParameter.PH, 6.5, 7.5)
        assertEquals(SessionTileState.ABOVE, sessionTileState("7,8", ph, established))
        assertEquals(SessionTileState.BELOW, sessionTileState("6.2", ph, established))
        assertEquals(SessionTileState.FAR_BELOW, sessionTileState("6.0", ph, established))
        assertEquals(SessionTileState.IN_RANGE, sessionTileState("7.0", ph, established))
    }

    @Test
    fun `nitrite far outside is far above`() {
        val nitrite = definition(BuiltInParameter.NITRITE, 0.0, 0.0)
        assertEquals(SessionTileState.FAR_ABOVE, sessionTileState("0.6", nitrite, established))
    }

    @Test
    fun `no indicative range never reads in range`() {
        val ph = definition(BuiltInParameter.PH)
        assertEquals(SessionTileState.NO_RANGE, sessionTileState("12", ph, established))
    }

    @Test
    fun `next on every tile but the last, which is done`() {
        assertEquals(ImeAction.Next, sessionImeAction(0, 2))
        assertEquals(ImeAction.Next, sessionImeAction(1, 2))
        assertEquals(ImeAction.Done, sessionImeAction(2, 2))
        assertEquals(ImeAction.Done, sessionImeAction(0, 0))
    }

    @Test
    fun `one column at 150 percent or under 340 dp`() {
        assertEquals(1, sessionColumns(1.5f, 412.dp))
        assertEquals(1, sessionColumns(1.0f, 330.dp))
        assertEquals(2, sessionColumns(1.0f, 412.dp))
    }

    @Test
    fun `count skips blank, invalid and inactive inputs`() {
        val ph = definition(BuiltInParameter.PH, 6.5, 7.5)
        val kh = definition(BuiltInParameter.KH)
        val gh = definition(BuiltInParameter.GH)
        val inputs = mapOf(ph.id to "7,8", kh.id to "", gh.id to "x", "other-id" to "5")
        assertEquals(1, validMeasurementCount(inputs, listOf(ph, kh, gh)))
    }
}
