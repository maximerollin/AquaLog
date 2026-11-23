package com.maximerollin.aqualog.onboarding

import com.maximerollin.aqualog.ParameterEditorState
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OnboardingPreviewTest {
    private fun volume(input: String) =
        onboardingPreviewAquarium("Salon", input, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED).volume

    private fun editor(min: String, max: String, unit: String = "pH", precision: String = "1") =
        ParameterEditorState(BuiltInParameter.PH, true, unit, precision, min, max)

    @Test
    fun `preview aquarium parses a comma volume`() {
        val aquarium = onboardingPreviewAquarium("Salon", "120,5", VolumeUnit.LITERS, AquariumProfile.ESTABLISHED)
        assertEquals(120.5, aquarium.volume)
        assertEquals("onboarding-preview", aquarium.id)
        assertEquals(0L, aquarium.createdAtEpochMillis)
    }

    @Test
    fun `preview aquarium keeps zero for a bad volume`() {
        listOf("", "abc", "-3", "0").forEach { assertEquals(0.0, volume(it), it) }
    }

    @Test
    fun `preview definition is null for an invalid editor`() {
        assertNull(editor("8", "6").toPreviewDefinition(0))
        assertNull(editor("6", "8", unit = " ").toPreviewDefinition(0))
    }

    @Test
    fun `preview definition carries the editor values`() {
        val definition = assertNotNull(editor("6,5", "7,5").toPreviewDefinition(3))
        assertEquals(6.5, definition.indicativeMinimum)
        assertEquals(7.5, definition.indicativeMaximum)
        assertEquals(1, definition.precision)
        assertEquals("ph", definition.id)
        assertEquals("onboarding-preview", definition.aquariumId)
        assertEquals(3, definition.position)
    }
}
