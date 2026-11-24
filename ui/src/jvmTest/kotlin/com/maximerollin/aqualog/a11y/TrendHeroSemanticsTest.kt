package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.TREND_HERO_STATE_TAG
import com.maximerollin.aqualog.TREND_HERO_VALUE_TAG
import com.maximerollin.aqualog.TrendHeroCard
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
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import com.maximerollin.aqualog.ui.theme.LEVEL_MARK_TAG
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TrendHeroSemanticsTest {
    private val day = 86_400_000L
    private val aquarium = Aquarium("a1", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.ESTABLISHED)
    private val ph = ParameterDefinition(
        id = "d-ph", aquariumId = aquarium.id, parameter = BuiltInParameter.PH, isActive = true, position = 0,
        unit = OnboardingPresets.presetUnit(BuiltInParameter.PH), precision = 1,
        indicativeMinimum = 6.5, indicativeMaximum = 7.5,
    )

    private fun series(vararg values: Double) = ParameterTrendSeries(
        ph,
        values.mapIndexed { index, value -> TrendPoint("s$index", (index + 1) * 10 * day, value) },
    )

    /** A 30-day window over the given series. */
    private fun snapshot(series: ParameterTrendSeries) = TrendSnapshot(
        aquarium = aquarium,
        period = TrendPeriod.THIRTY_DAYS,
        windowStartEpochMillis = 0L,
        windowEndEpochMillis = 30 * day,
        series = listOf(series),
        events = emptyList(),
    )

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun SemanticsNodeInteractionsProvider.countText(substring: String) =
        onAllNodes(hasText(substring, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `hero says the state in words and the change over the period`() = withLocale(Locale.ENGLISH) {
        runComposeUiTest {
            val series = series(7.1, 7.8)
            setContent { AquaLogTheme(false) { TrendHeroCard(series, snapshot(series), expanded = false, onToggleValues = {}) } }
            onNodeWithText("Above the range", useUnmergedTree = true).assertExists()
            onNodeWithText("+0.7 over 30 days", useUnmergedTree = true).assertExists()
            assertEquals(1, onAllNodesWithTag(LEVEL_MARK_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `one point gives no change line`() = withLocale(Locale.ENGLISH) {
        runComposeUiTest {
            val series = series(7.8)
            setContent { AquaLogTheme(false) { TrendHeroCard(series, snapshot(series), expanded = false, onToggleValues = {}) } }
            onNodeWithText("Above the range", useUnmergedTree = true).assertExists()
            assertEquals(0, countText("over"))
        }
    }

    @Test
    fun `the chart keeps its description`() = withLocale(Locale.ENGLISH) {
        runComposeUiTest {
            val series = series(7.1, 7.8)
            setContent { AquaLogTheme(false) { TrendHeroCard(series, snapshot(series), expanded = false, onToggleValues = {}) } }
            onNodeWithContentDescription(
                "pH trend chart · solid line and circle point markers · hatched indicative range · diamond Event markers",
            ).assertExists()
        }
    }

    @Test
    fun `exact values toggle is a 48 dp button`() = withLocale(Locale.ENGLISH) {
        runComposeUiTest {
            val series = series(7.1, 7.8)
            var toggled = 0
            setContent {
                AquaLogTheme(false) { TrendHeroCard(series, snapshot(series), expanded = false, onToggleValues = { toggled++ }) }
            }
            val button = onNodeWithText("Exact values")
            val bounds = button.fetchSemanticsNode().boundsInRoot
            val minimum = with(density) { 48.dp.toPx() }
            assertTrue(bounds.height >= minimum - 0.5f, "height ${bounds.height} < $minimum")
            button.performClick()
            assertEquals(1, toggled)
        }
    }

    @Test
    fun `german at 150 percent keeps value and state apart`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            val series = series(7.1, 7.8)
            setContent {
                AquaLogTheme(false) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
                        Box(Modifier.width(360.dp)) {
                            TrendHeroCard(series, snapshot(series), expanded = false, onToggleValues = {})
                        }
                    }
                }
            }
            val state = onNodeWithTag(TREND_HERO_STATE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val value = onNodeWithTag(TREND_HERO_VALUE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(state.width > 0f && value.width > 0f)
            assertFalse(state.overlaps(value), "state $state overlaps value $value")
        }
    }
}
