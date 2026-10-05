package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumPersistenceTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun onboarding_createsConfiguredAquarium_withoutPersistingPracticeValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(AQUA_LOG_DATABASE_NAME)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Cycling").performClick()
        composeRule.onNodeWithText("Continue to Aquarium").performClick()

        composeRule.onNodeWithText("Aquarium name")
            .performTextInput("Amazonien")
        composeRule.onNodeWithText("Volume")
            .performTextInput("120.5")
        composeRule.onNodeWithText("US gallons")
            .performClick()
        composeRule.onNodeWithText("Continue to Parameters")
            .performClick()

        composeRule.onNodeWithText("Ammonia / ammonium")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Enabled for Ammonia / ammonium")
            .assertIsOn()
        val temperatureUnit = composeRule.onNodeWithContentDescription("Unit for Temperature")
        temperatureUnit.performScrollTo()
        temperatureUnit.performTextClearance()
        temperatureUnit.performTextInput("°F")
        composeRule.onNodeWithContentDescription("Move pH up")
            .performScrollTo()
            .performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Try a mini Session"))
        composeRule.onNodeWithText("Try a mini Session").performClick()

        composeRule.onNodeWithContentDescription("Practice value for pH")
            .performTextInput("6.8")
        composeRule.onNodeWithText("See Free and Pro").performClick()
        composeRule.onNodeWithText("Continue for free").performClick()

        composeRule.onNodeWithText("Amazonien").assertIsDisplayed()
        composeRule.onNodeWithText("120.5 US gal").assertIsDisplayed()
        composeRule.onNodeWithText("No Session yet").assertIsDisplayed()
        composeRule.onNodeWithText("New Session").assertIsDisplayed()

        scenario.recreate()

        composeRule.onNodeWithText("Amazonien").assertIsDisplayed()
        composeRule.onNodeWithText("120.5 US gal").assertIsDisplayed()
        composeRule.onNodeWithText("No Session yet").assertIsDisplayed()
        scenario.close()
    }
}
