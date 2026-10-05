package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun createdAquarium_isDisplayedAfterActivityRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(AQUA_LOG_DATABASE_NAME)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText(context.getString(R.string.aquarium_name_label))
            .performTextInput("Amazonien")
        composeRule.onNodeWithText(context.getString(R.string.aquarium_volume_label))
            .performTextInput("120.5")
        composeRule.onNodeWithText(context.getString(R.string.unit_us_gallons))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.save_aquarium))
            .performClick()

        composeRule.onNodeWithText("Amazonien").assertIsDisplayed()
        composeRule.onNodeWithText("120.5 US gal").assertIsDisplayed()

        scenario.recreate()

        composeRule.onNodeWithText("Amazonien").assertIsDisplayed()
        composeRule.onNodeWithText("120.5 US gal").assertIsDisplayed()
        scenario.close()
    }
}
