package com.maximerollin.aqualog

import androidx.compose.ui.geometry.Offset
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.ParameterTrendSeries
import com.maximerollin.aqualog.shared.TrendPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class TrendSparklineTest {
    private fun series(vararg points: Pair<Long, Double>) = ParameterTrendSeries(
        definition = ParameterDefinition(
            id = "ph", aquariumId = "a", parameter = BuiltInParameter.PH, isActive = true, position = 0,
            unit = "pH", precision = 1, indicativeMinimum = 6.5, indicativeMaximum = 7.5,
        ),
        points = points.mapIndexed { index, (time, value) -> TrendPoint("s$index", time, value) },
    )

    @Test
    fun `points sit at their time in the window and the range fills the height`() {
        val result = sparklinePoints(series(0L to 6.5, 1_000L to 7.5), 0L, 1_000L, 100f, 30f)
        assertEquals(listOf(Offset(0f, 30f), Offset(100f, 0f)), result)
    }

    @Test
    fun `a single point in the middle of the window is centred horizontally`() {
        val result = sparklinePoints(series(500L to 7.0), 0L, 1_000L, 100f, 30f)
        assertEquals(50f, result.single().x)
    }

    @Test
    fun `a point outside the range widens the scale`() {
        val result = sparklinePoints(series(0L to 6.5, 1_000L to 8.0), 0L, 1_000L, 100f, 30f)
        assertEquals(0f, result[1].y)
        assertEquals(30f, result[0].y)
    }
}
