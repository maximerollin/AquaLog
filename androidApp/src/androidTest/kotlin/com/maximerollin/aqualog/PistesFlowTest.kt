package com.maximerollin.aqualog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PistesFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun outOfRangeNitrite_showsPistesAfterSaving_andOnTheHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Get started")
            completeMinimalOnboarding()
            composeRule.onNodeWithText("Measure").performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Nitrite").performTextInput("0.25")
            composeRule.onNodeWithText("Save Session").performClick()

            waitForText("Ideas for Nitrite")
            composeRule.onNodeWithText("Change 30% of the water.").assertIsDisplayed()
            composeRule.onNodeWithText("Test again tomorrow.").assertIsDisplayed()
            composeRule.onNodeWithText("These are ideas, not a diagnosis", substring = true).assertIsDisplayed()

            composeRule.onNodeWithText("Close").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Ideas for Nitrite")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("Ideas · Nitrite").performClick()
            composeRule.onNodeWithText("Change 30% of the water.").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun sheetShowsOnce_leavingTheHomeWithoutClosingDismissesIt_andTheCardStays() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Get started")
            completeMinimalOnboarding()
            composeRule.onNodeWithText("Measure").performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Nitrite").performTextInput("0.25")
            composeRule.onNodeWithText("Save Session").performClick()
            waitForText("Ideas for Nitrite")

            // The scrim blocks touches, so leave the home through the semantics action, as a programmatic navigation would.
            composeRule.onNodeWithText("History").performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Chronology")
            composeRule.onNodeWithText("Tank").performClick()
            waitForText("Ideas · Nitrite")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Ideas for Nitrite")).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            scenario.close()
        }
    }

    private fun completeMinimalOnboarding() {
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Continue to Aquarium").performClick()
        composeRule.onNodeWithText("Aquarium name").performTextInput("Amazonien")
        composeRule.onNodeWithText("Volume").performTextInput("120")
        composeRule.onNodeWithText("Continue to Parameters").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Try a mini Session"))
        composeRule.onNodeWithText("Try a mini Session").performClick()
        composeRule.onNodeWithText("See Free and Pro").performClick()
        composeRule.onNodeWithText("Continue for free").performClick()
        waitForText("Measure")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
