package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrendsFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun opensAccessibleFreeTrend_andConsultsExactValuesForDistinctUnits() {
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
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    idempotencyKey = "trends-test",
                    measurementInputs = mapOf(temperature.id to "24.5", ph.id to "7.2"),
                    maintenanceActions = listOf(
                        MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                    ),
                    incident = "Heater stopped",
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Trends").performClick().assertIsSelected()
        composeRule.onNodeWithText("30 days").assertIsSelected()
        composeRule.onNodeWithText("7 days").assertIsDisplayed()
        composeRule.onNodeWithText("90 days").assertIsDisplayed()
        composeRule.onNodeWithText("Temperature").assertIsSelected()
        composeRule.onNodeWithText("Indicative target 22–28 °C · reference only, not a diagnosis")
            .assertIsDisplayed()
        val chartDescription = "Temperature trend chart · solid line and circle point markers · " +
            "hatched indicative target · diamond Event markers"
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription(chartDescription))
        composeRule.onNodeWithContentDescription(chartDescription).assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 24.5 °C"))
        composeRule.onNodeWithText("Exact values").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Exact value 24.5 °C").assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Water change · Incident: Heater stopped", substring = true))
        composeRule.onNodeWithText("Water change · Incident: Heater stopped", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Free trends show up to 90 days. Older data stays saved.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("pH"))
        composeRule.onNodeWithText("pH").performClick().assertIsSelected()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 7.2 pH"))
        composeRule.onNodeWithContentDescription("Exact value 7.2 pH").assertIsDisplayed()
        scenario.close()
    }
}
