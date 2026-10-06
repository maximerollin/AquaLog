package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
        composeRule.onNodeWithText("Offline-ready · stored on this device").assertIsDisplayed()
        composeRule.onNodeWithText("1 Measurement · 1 maintenance action · 1 Event").assertIsDisplayed()

        composeRule.onNodeWithText("Temperature").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Open Session details").performClick()
        composeRule.onNodeWithText("Session details").assertIsDisplayed()
        composeRule.onNodeWithText("Temperature · 24.5 °C").assertIsDisplayed()
        composeRule.onNodeWithText("Water change · 20 %").assertIsDisplayed()
        composeRule.onNodeWithText("Observation · Clear water").assertIsDisplayed()

        composeRule.onNodeWithText("Back to Chronology").performClick()
        composeRule.onNodeWithText("Temperature").assertIsSelected()
        scenario.close()
    }
}
