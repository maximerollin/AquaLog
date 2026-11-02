package com.maximerollin.aqualog.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarnetColorTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test
    fun `light background is the warm paper`() {
        assertEquals(Color(0xFFF3EFE3), aquaLogColorScheme(false).background)
    }

    @Test
    fun `text pairs reach 4_5 to 1 on paper in both schemes`() {
        for (dark in listOf(false, true)) {
            val scheme = aquaLogColorScheme(dark)
            val attention = if (dark) AttentionTextDark else AttentionText
            assertTrue(contrast(scheme.onBackground, scheme.background) >= 4.5f, "onBackground dark=$dark")
            assertTrue(contrast(scheme.primary, scheme.background) >= 4.5f, "primary dark=$dark")
            assertTrue(contrast(attention, scheme.background) >= 4.5f, "attention dark=$dark")
        }
    }

    @Test
    fun `attention mark reaches 3 to 1 on paper in both schemes`() {
        for (dark in listOf(false, true)) {
            val mark = if (dark) AttentionMarkDark else AttentionMark
            assertTrue(contrast(mark, aquaLogColorScheme(dark).background) >= 3.0f, "attention mark dark=$dark")
        }
    }
}
