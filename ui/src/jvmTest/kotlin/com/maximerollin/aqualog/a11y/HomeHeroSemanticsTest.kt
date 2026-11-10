package com.maximerollin.aqualog.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.maximerollin.aqualog.HomeOverview
import com.maximerollin.aqualog.HomeValue
import com.maximerollin.aqualog.home.HomeHero
import com.maximerollin.aqualog.home.MiniScene
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.assistance.ReadingLevel
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeHeroSemanticsTest {
    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 10)
        val TODAY_MILLIS = TODAY.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val SALON = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L, AquariumProfile.PLANTED_SHRIMP)
        val RATIO = Regex("\\d+/\\d+")
    }

    private fun withEnglish(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun definition(index: Int) = ParameterDefinition(
        id = "p$index", aquariumId = "a", parameter = BuiltInParameter.PH, isActive = true, position = index,
        unit = "pH", precision = 1, indicativeMinimum = 6.5, indicativeMaximum = 7.5,
    )

    /** Seven measured values, one of them outside its range. */
    private val sixOfSeven: HomeOverview = List(7) { index ->
        HomeValue(definition(index), 7.0, TODAY_MILLIS, if (index == 0) ReadingLevel.ATTENTION else ReadingLevel.OK, false)
    }.let { values -> HomeOverview(values, outsideRangeCount = 1, latestReadingAtEpochMillis = TODAY_MILLIS, all = values) }

    private val unmeasured: HomeOverview = List(3) { index -> HomeValue(definition(index), null, null, ReadingLevel.OK, false) }
        .let { values -> HomeOverview(values, outsideRangeCount = 0, latestReadingAtEpochMillis = null, all = values) }

    private fun hasTextMatching(regex: Regex) = SemanticsMatcher("text matches $regex") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { regex.containsMatchIn(it.text) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.hero(
        overview: HomeOverview?,
        inhabitantCount: Int = 16,
        onOpenPhoto: (() -> Unit)? = {},
        onOpenStatus: (() -> Unit)? = null,
    ) = setContent {
        AquaLogTheme(false) {
            HomeHero(
                aquarium = SALON, overview = overview, today = TODAY, inhabitantCount = inhabitantCount,
                scene = MiniScene.Empty, still = true, photo = null,
                onChangeAquarium = {}, onOpenPhoto = onOpenPhoto, onOpenHabitants = {}, onAddHabitants = {},
                onOpenStatus = onOpenStatus,
            )
        }
    }

    @Test
    fun `unmeasured aquarium shows no ring and the no-measurement title`() = withEnglish {
        runComposeUiTest {
            hero(unmeasured)
            onAllNodes(hasTextMatching(RATIO), useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("status-ring", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("No measurement yet").assertExists()
        }
    }

    @Test
    fun `ratio card reads six of seven and is a button when pistes exist`() = withEnglish {
        runComposeUiTest {
            hero(sixOfSeven, onOpenStatus = {})
            onNodeWithTag("status-ring", useUnmergedTree = true).assertExists()
            onNodeWithText("6 of 7 in range", substring = true).assertExists().assertHasClickAction()
        }
    }

    @Test
    fun `name block changes aquarium and camera opens the photo`() = withEnglish {
        runComposeUiTest {
            var opened = 0
            hero(sixOfSeven, onOpenPhoto = { opened++ })
            val name = onNode(hasText("Salon", substring = false)).fetchSemanticsNode()
            assertEquals("Change Aquarium", name.config[SemanticsActions.OnClick].label)
            onNodeWithContentDescription("Add a photo of Salon").performClick()
            assertEquals(1, opened)
        }
    }

    @Test
    fun `inhabitants context is omitted at zero`() = withEnglish {
        runComposeUiTest {
            hero(sixOfSeven, inhabitantCount = 0)
            val texts = onNode(hasText("Salon", substring = false)).fetchSemanticsNode()
                .config[SemanticsProperties.Text].map { it.text }
            assertTrue(texts.any { "120" in it }, "$texts")
            assertFalse(texts.any { "inhabitant" in it.lowercase() }, "$texts")
        }
    }

    @Test
    fun `a page that is not the current aquarium has no status card`() = withEnglish {
        runComposeUiTest {
            hero(overview = null, onOpenPhoto = null)
            onNodeWithText("in range", substring = true, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("No measurement yet", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag("status-ring", useUnmergedTree = true).assertDoesNotExist()
        }
    }
}
