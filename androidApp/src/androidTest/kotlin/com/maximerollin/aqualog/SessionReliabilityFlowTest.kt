package com.maximerollin.aqualog

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionReliabilityFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun draft_survivesBackgroundingAndConfigurationChange() {
        val scenario = launchConfiguredAquarium()
        composeRule.onNode(hasLabel("Measure")).performClick()
        waitForText("Measurements")

        val temperature = composeRule.onNodeWithContentDescription("Measurement for Temperature")
        temperature.performTextReplacement("24,5")
        temperature.assertTextContains("24,5")
        closeSoftKeyboard()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithContentDescription("Measurement for Temperature")
            .assertTextContains("24,5")

        scenario.recreate()
        waitForText("Measurements")
        composeRule.onNodeWithContentDescription("Measurement for Temperature")
            .assertTextContains("24,5")
        scenario.close()
    }

    @Test
    fun recordedSession_canBeReopenedCorrectedAfterRestartAndDeletedWithConfirmation() {
        var scenario = launchConfiguredAquarium()
        composeRule.onNode(hasLabel("Measure")).performClick()
        waitForText("Measurements")
        composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Water change"))
        composeRule.onNodeWithText("Water change").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add observation"))
        composeRule.onNodeWithText("Add observation").performClick()
        composeRule.onNodeWithText("Observation").performTextInput("Eau claire")
        composeRule.onNodeWithTag("save-session").performClick()

        waitForText("measured today", substring = true)
        composeRule.onNode(hasText("measured today", substring = true) and hasClickAction()).performClick()
        // The header's subtitle: « Session details · <Aquarium> », under the date.
        composeRule.onNode(hasText("Session details", substring = true)).assertIsDisplayed()
        // A reading is one tile, spoken as one sentence: its label, value and unit.
        composeRule.onNode(hasText("Temperature, 24.5, °C", substring = true)).assertIsDisplayed()
        // Maintenance and notes are rows: label and detail, kind and note, each one node.
        val waterChange = hasText("Water change") and hasText("20 %")
        composeRule.onNodeWithTag("session-details-list").performScrollToNode(waterChange)
        composeRule.onNode(waterChange).assertIsDisplayed()
        val observation = hasText("Observation") and hasText("Eau claire")
        composeRule.onNodeWithTag("session-details-list").performScrollToNode(observation)
        composeRule.onNode(observation).assertIsDisplayed()

        composeRule.onNodeWithTag("session-details-list").performScrollToNode(hasText("Edit Session"))
        composeRule.onNodeWithText("Edit Session").performClick()
        waitForContentDescription("Measurement for Temperature")
        composeRule.onNodeWithTag("save-session").assert(hasText("Save changes"))
        val temperature = composeRule.onNodeWithContentDescription("Measurement for Temperature")
        temperature.performTextClearance()
        temperature.performTextInput("25,1")
        closeSoftKeyboard()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Water change"))
        composeRule.onNodeWithText("Water change").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Fertilization"))
        composeRule.onNodeWithText("Fertilization").assertIsDisplayed().performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1 mL"))
        waitForText("1 mL")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Hide observation"))
        composeRule.onNodeWithText("Observation").performTextClearance()
        closeSoftKeyboard()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add incident"))
        composeRule.onNodeWithText("Add incident").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Hide incident"))
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Incident"))
        composeRule.onNodeWithText("Incident").performTextInput("Filtre corrigé")
        composeRule.onNodeWithTag("save-session").performClick()

        waitForText("Session details", substring = true)
        waitForText("25.1", substring = true)
        composeRule.onNode(hasText("Temperature, 25.1, °C", substring = true)).assertIsDisplayed()
        val fertilization = hasText("Fertilization") and hasText("1 mL")
        composeRule.onNodeWithTag("session-details-list").performScrollToNode(fertilization)
        composeRule.onNode(fertilization).assertIsDisplayed()
        val incident = hasText("Incident") and hasText("Filtre corrigé")
        composeRule.onNodeWithTag("session-details-list").performScrollToNode(incident)
        composeRule.onNode(incident).assertIsDisplayed()
        scenario.close()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("measured today", substring = true)
        composeRule.onNode(hasText("measured today", substring = true) and hasClickAction()).performClick()
        waitForText("25.1", substring = true)
        composeRule.onNode(hasText("Temperature, 25.1, °C", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithTag("session-details-list").performScrollToNode(hasText("Delete Session"))
        composeRule.onNodeWithText("Delete Session").performClick()
        composeRule.onNodeWithText("Delete this Session?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()

        waitForText("No measurement yet", substring = true)
        scenario.close()
    }

    private fun launchConfiguredAquarium(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Get started")
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Continue to Aquarium").performClick()
        composeRule.onNodeWithText("Aquarium name").performTextInput("Amazonien")
        composeRule.onNodeWithText("Volume").performTextInput("120")
        composeRule.onNodeWithText("Continue to Parameters").performClick()
        composeRule.scrollToText("Try a mini Session")
        composeRule.onNodeWithText("Try a mini Session").performClick()
        composeRule.onNodeWithText("See Free and Pro").performClick()
        composeRule.onNodeWithText("Continue for free").performClick()
        composeRule.awaitLabel("Measure")
        return scenario
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(description),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
