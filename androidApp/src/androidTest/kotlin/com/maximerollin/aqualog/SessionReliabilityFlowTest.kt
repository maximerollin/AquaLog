package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
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
        composeRule.onNodeWithText("New Session").performClick()
        waitForText("Measurements")

        val temperature = composeRule.onNodeWithContentDescription("Measurement for Temperature")
        temperature.performTextInput("24,5")
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        temperature.assertTextContains("24,5")

        scenario.recreate()
        waitForText("Measurements")
        composeRule.onNodeWithContentDescription("Measurement for Temperature")
            .assertTextContains("24,5")
        scenario.close()
    }

    @Test
    fun recordedSession_canBeReopenedCorrectedAfterRestartAndDeletedWithConfirmation() {
        var scenario = launchConfiguredAquarium()
        composeRule.onNodeWithText("New Session").performClick()
        waitForText("Measurements")
        composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Water change"))
        composeRule.onNodeWithText("Water change").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add observation"))
        composeRule.onNodeWithText("Add observation").performClick()
        composeRule.onNodeWithText("Observation").performTextInput("Eau claire")
        composeRule.onNodeWithText("Save Session").performClick()

        waitForText("Session saved")
        composeRule.onNodeWithText("View Session details").performClick()
        composeRule.onNodeWithText("Session details").assertIsDisplayed()
        composeRule.onNodeWithText("Temperature · 24.5 °C").assertIsDisplayed()
        composeRule.onNodeWithText("Water change · 20 %").assertIsDisplayed()
        composeRule.onNodeWithText("Observation · Eau claire").assertIsDisplayed()

        composeRule.onNodeWithText("Edit Session").performClick()
        waitForContentDescription("Measurement for Temperature")
        val temperature = composeRule.onNodeWithContentDescription("Measurement for Temperature")
        temperature.performTextClearance()
        temperature.performTextInput("25,1")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Water change"))
        composeRule.onNodeWithText("Water change").performClick()
        composeRule.onNodeWithText("Fertilization").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Hide observation"))
        composeRule.onNodeWithText("Observation").performTextClearance()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add incident"))
        composeRule.onNodeWithText("Add incident").performClick()
        waitForText("Hide incident")
        composeRule.onNodeWithText("Incident").performTextInput("Filtre corrigé")
        composeRule.onNodeWithText("Save changes").performClick()

        waitForText("Session details")
        waitForText("Temperature · 25.1 °C")
        composeRule.onNodeWithText("Temperature · 25.1 °C").assertIsDisplayed()
        composeRule.onNodeWithText("Fertilization · 1 mL").assertIsDisplayed()
        composeRule.onNodeWithText("Incident · Filtre corrigé").assertIsDisplayed()
        scenario.close()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Session saved")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("View Session details"))
        composeRule.onNodeWithText("View Session details").performClick()
        waitForText("Temperature · 25.1 °C")
        composeRule.onNodeWithText("Temperature · 25.1 °C").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Session").performClick()
        composeRule.onNodeWithText("Delete this Session?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()

        waitForText("No Session yet")
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
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Try a mini Session"))
        composeRule.onNodeWithText("Try a mini Session").performClick()
        composeRule.onNodeWithText("See Free and Pro").performClick()
        composeRule.onNodeWithText("Continue for free").performClick()
        waitForText("New Session")
        return scenario
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
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
