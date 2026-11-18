package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.RhythmContent
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Analysis rhythm rows expanding in place to their choices (lot 4a, Task 4). */
@OptIn(ExperimentalTestApi::class)
class RhythmSemanticsTest {
    private val salon = Aquarium("a", "Salon", 120.0, VolumeUnit.LITERS, 0L)

    private fun parameter(id: String, parameter: BuiltInParameter, position: Int, days: Int? = null) =
        ParameterDefinition(id, salon.id, parameter, true, position, "", 1, null, null, days)

    private fun setup(phDays: Int? = null) = AquariumSetup(
        salon,
        listOf(
            parameter("ph", BuiltInParameter.PH, 0, phDays),
            parameter("gh", BuiltInParameter.GH, 1),
        ),
    )

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val updates = mutableListOf<Pair<String, Int?>>()

    private fun ComposeUiTest.rhythm(setup: AquariumSetup, fontScale: Float = 1f) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp).testTag("frame")) {
                        RhythmContent(setup, { id, days -> updates += id to days }, {})
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a row expands to its choices and collapses after a choice`() = withLocale(Locale.FRENCH) {
        runComposeUiTest {
            updates.clear()
            rhythm(setup())
            val phRow = hasText("pH")
            onNode(phRow).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Réduit"))
            onAllNodes(radio).assertCountEquals(0)
            onNode(phRow).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            onNode(phRow).performClick()
            onNode(phRow).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Développé"))
            onAllNodes(radio).assertCountEquals(8)
            onNode(radio and hasText("3 jours")).assertHeightIsAtLeast(48.dp)
            onNode(radio and hasText("3 jours")).performClick()
            assertEquals(listOf<Pair<String, Int?>>("ph" to 3), updates)
            onNode(phRow).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Réduit"))
            onAllNodes(radio).assertCountEquals(0)
        }
    }

    @Test
    fun `only one row is expanded`() = withLocale(Locale.FRENCH) {
        runComposeUiTest {
            rhythm(setup())
            onNode(hasText("pH")).performClick()
            onNode(hasText("GH")).performClick()
            onNode(hasText("pH")).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Réduit"))
            onNode(hasText("GH")).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Développé"))
            onAllNodes(radio).assertCountEquals(8)
        }
    }

    @Test
    fun `the current choice is selected`() = withLocale(Locale.FRENCH) {
        runComposeUiTest {
            rhythm(setup(phDays = 7))
            onNode(hasText("pH")).performClick()
            onNode(isSelectable() and hasText("7 jours")).assertIsSelected()
            onAllNodes(isSelectable() and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
        }
    }

    @Test
    fun `choices wrap at 150 percent in German`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            rhythm(setup(), fontScale = 1.5f)
            onNode(hasText("pH-Wert")).performClick()
            val frame = onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            val chips = onAllNodes(radio).fetchSemanticsNodes()
            assertEquals(8, chips.size)
            (0 until 8).forEach { onAllNodes(radio)[it].assertIsDisplayed() }
            chips.forEach { node ->
                assertTrue(node.boundsInRoot.left >= frame.left && node.boundsInRoot.right <= frame.right, "chip ${node.boundsInRoot} in $frame")
            }
            // Several lines: they do not all sit on one row.
            assertTrue(chips.map { it.boundsInRoot.top }.distinct().size > 1)
        }
    }

    @Test
    fun `a long current choice does not squeeze the label at 150 percent in German`() = withLocale(Locale.GERMAN) {
        runComposeUiTest {
            // The longest label: « Ammoniak / Ammonium » with the Cycling default « Auto (2 Tage) ».
            val cycling = AquariumSetup(
                salon.copy(profile = AquariumProfile.CYCLING),
                listOf(parameter("nh", BuiltInParameter.AMMONIA, 0)),
            )
            rhythm(cycling, fontScale = 1.5f)
            val row = onNode(hasText("Ammoniak / Ammonium")).fetchSemanticsNode().boundsInRoot
            val label = onNode(hasText("Ammoniak / Ammonium"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            onNode(hasText("Ammoniak / Ammonium"), useUnmergedTree = true).assertIsDisplayed()
            assertTrue(label.width >= row.width * .4f, "label $label in row $row")
        }
    }
}
