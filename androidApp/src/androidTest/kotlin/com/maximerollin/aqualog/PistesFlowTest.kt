package com.maximerollin.aqualog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
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
            composeRule.onNode(hasLabel("Measure")).performClick()
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
            // The ideas stay one tap away: the home's status card reopens them.
            composeRule.onNodeWithTag("status-card").performClick()
            waitForText("Ideas for Nitrite")
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
            composeRule.onNode(hasLabel("Measure")).performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Nitrite").performTextInput("0.25")
            composeRule.onNodeWithText("Save Session").performClick()
            waitForText("Ideas for Nitrite")

            // The scrim blocks touches, so leave the home through the semantics action, as a programmatic navigation would.
            composeRule.onNode(hasLabel("History")).performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Chronology")
            composeRule.onNode(hasLabel("Tank")).performClick()
            waitForTag("status-card")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Ideas for Nitrite")).fetchSemanticsNodes().isEmpty()
            }
            // The sheet does not come back by itself, but the status card still opens it.
            composeRule.onNodeWithTag("status-card").performClick()
            waitForText("Ideas for Nitrite")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun alertNitrite_isNamedInTheEntryHint_andInTheStatusLine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Get started")
            completeMinimalOnboarding()
            composeRule.onNode(hasLabel("Measure")).performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Nitrite").performTextInput("0.6")
            composeRule.onNodeWithText("Far outside your indicative range", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Save Session").performClick()
            waitForText("Ideas for Nitrite")
            composeRule.onNodeWithText("Close").performClick()
            // The status line reads « 1 value to watch, 1 far out of range · measured today » as one text.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("1 value to watch, 1 far out of range", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun anOlderOutOfRangeReading_datesTheRetestFromItsDay() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = System.currentTimeMillis() - 86_400_000L,
                    idempotencyKey = "dated-retest",
                    measurementInputs = mapOf(setup.parameters.first { it.parameter == BuiltInParameter.NITRATE }.id to "60"),
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForTag("status-card")
            composeRule.onNodeWithTag("status-card").performClick()
            waitForText("Ideas for Nitrate")
            // Nitrate is tested again 3 days after yesterday's reading: a date, neither today nor tomorrow.
            composeRule.onNodeWithText("Test again on ", substring = true).assertIsDisplayed()
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
        composeRule.awaitLabel("Measure")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
