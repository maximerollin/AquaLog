package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun filtersSession_opensDetail_andReturnsToPreservedFilters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val temperature = setup.parameters.first(ParameterDefinition::isActive)
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    idempotencyKey = "timeline-test",
                    measurementInputs = mapOf(temperature.id to "24.5"),
                    maintenanceActions = listOf(
                        MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                    ),
                    observation = "Clear water",
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Chronology").assertIsDisplayed()
        // One Session: its reading, maintenance action and Event all show on its journal entry.
        composeRule.onNode(
            hasContentDescription("Open Session details") and hasText("24.5", substring = true) and hasClickAction(),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Water change · 20 %").assertIsDisplayed()
        composeRule.onNodeWithText("Clear water").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Filters").performClick()
        composeRule.onNodeWithText("Offline-ready · stored on this device").assertIsDisplayed()
        composeRule.onNode(hasText("Temperature") and isSelectable()).performClick().assertIsSelected()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithContentDescription("Open Session details").performClick()
        composeRule.onNodeWithText("Session details").assertIsDisplayed()
        composeRule.onNode(hasText("Temperature") and hasText("24.5")).assertIsDisplayed()
        composeRule.onNodeWithText("Water change · 20 %").assertIsDisplayed()
        composeRule.onNodeWithText("Observation · Clear water").assertIsDisplayed()

        composeRule.onNodeWithText("Back to Chronology").performClick()
        composeRule.onNodeWithContentDescription("Filters").performClick()
        composeRule.onNode(hasText("Temperature") and isSelectable()).assertIsSelected()
        scenario.close()
    }
}
