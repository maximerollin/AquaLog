package com.maximerollin.aqualog

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** System Back closes the screen shown, like its own close or back control, instead of leaving the app. */
@RunWith(AndroidJUnit4::class)
class BackNavigationFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication

    @Before
    fun freeAquarium() {
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                "Back tank", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
    }

    @Test
    fun freeUser_backOnTheProOfferFromTrends_returnsToTrends() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.awaitLabel("Trends")
            // The Trends tab, by its role.
            composeRule.onNode(hasLabel("Trends") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            composeRule.awaitLabel("Custom period")
            composeRule.onNode(hasLabel("Custom period")).performClick()
            composeRule.awaitLabel("Choose Free or Pro")

            pressBack()

            composeRule.awaitLabel("Custom period")
            composeRule.onAllNodesWithText("Choose Free or Pro").assertCountEquals(0)
            composeRule.onNodeWithTag("trend-list").assertIsDisplayed()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backOnHistory_returnsToTheHome() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.awaitLabel("History")
            composeRule.onNode(hasLabel("History")).performClick()
            composeRule.awaitLabel("Chronology")

            pressBack()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("home-list")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText("Chronology").assertCountEquals(0)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backOnASessionOpenedWithPlus_returnsToTheHome_withoutSaving() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.awaitLabel("Measure")
            composeRule.onNode(hasLabel("Measure")).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Measurements")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
            // While the keyboard shows, Back only hides it: hide it first, and wait until it is gone.
            closeSoftKeyboard()
            composeRule.waitUntil(timeoutMillis = 10_000) { !scenario.imeVisible() }

            pressBack()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("home-list")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText("Measurements").assertCountEquals(0)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            val sessions = runBlocking { application.aquariumRepository.observeTimeline(TimelineFilter()).first() }
            assertTrue("$sessions", sessions.isEmpty())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backOnRhythmWithARowOpen_foldsTheRow_thenLeavesTheScreen() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.awaitLabel("More")
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.scrollAboveBar("settings-list", hasText("Analysis rhythm"))
            composeRule.onNodeWithText("Analysis rhythm").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("rhythm-list")).fetchSemanticsNodes().isNotEmpty()
            }
            val temperature = hasText("Temperature") and hasClickAction()
            composeRule.onNodeWithTag("rhythm-list").performScrollToNode(temperature)
            composeRule.onNode(temperature).performClick()
            composeRule.onNode(temperature).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            composeRule.onAllNodesWithText("Never").assertCountEquals(1)

            pressBack()

            // Still on Rhythm, the row folded.
            composeRule.onNode(temperature).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            composeRule.onAllNodesWithText("Never").assertCountEquals(0)
            composeRule.onNodeWithTag("rhythm-list").assertIsDisplayed()

            pressBack()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("rhythm-list")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("settings-list").assertIsDisplayed()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    private fun ActivityScenario<MainActivity>.imeVisible(): Boolean {
        var visible = true
        onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}
