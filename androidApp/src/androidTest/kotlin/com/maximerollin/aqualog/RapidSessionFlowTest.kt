package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RapidSessionFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun routine_recordsThreeMeasurementsAndWaterChange_inOneContinuousView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Get started")).fetchSemanticsNodes().isNotEmpty()
        }
        completeMinimalOnboarding()

        composeRule.onNodeWithText("New Session").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Measurements")).fetchSemanticsNodes().isNotEmpty()
        }
        val save = composeRule.onNodeWithText("Save Session")
        save.assertIsNotEnabled()

        val temperature = composeRule.onNodeWithContentDescription("Measurement for Temperature")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { temperature.assertIsFocused() }.isSuccess
        }
        temperature.assertIsFocused()
        temperature.performTextInput("24,5")
        temperature.performImeAction()
        val ph = composeRule.onNodeWithContentDescription("Measurement for pH")
        ph.assertIsFocused()
        ph.performTextInput("7.2")
        ph.performImeAction()
        val nitrite = composeRule.onNodeWithContentDescription("Measurement for Nitrite")
        nitrite.assertIsFocused()
        nitrite.performTextInput("0")

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Water change"))
        composeRule.onNodeWithText("Water change").performClick()
        save.assertIsEnabled().performClick()

        composeRule.onNodeWithText("Session saved").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Keep this Aquarium on your devices")).assertCountEquals(0)
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Keep this Aquarium on your devices").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Session saved").assertIsDisplayed()
        composeRule.onNodeWithText("Chronology confirmation · 3 Measurements · 1 maintenance actions")
            .assertIsDisplayed()
        scenario.close()
    }

    @Test
    fun accountInvitation_returnsAfterActivityRestart_whenConfirmationWasShownButNotContinued() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val firstScenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Get started")).fetchSemanticsNodes().isNotEmpty()
        }
        completeMinimalOnboarding()

        composeRule.onNodeWithText("New Session").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Measurements")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24.5")
        composeRule.onNodeWithText("Save Session").performClick()
        composeRule.onNodeWithText("Session saved").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Keep this Aquarium on your devices")).assertCountEquals(0)
        firstScenario.close()

        val restartedScenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Keep this Aquarium on your devices"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Keep this Aquarium on your devices").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()
        restartedScenario.close()

        val acknowledgedScenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("New Session")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText("Keep this Aquarium on your devices")).assertCountEquals(0)
        acknowledgedScenario.close()
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
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("New Session")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Amazonien").assertIsDisplayed()
    }
}
