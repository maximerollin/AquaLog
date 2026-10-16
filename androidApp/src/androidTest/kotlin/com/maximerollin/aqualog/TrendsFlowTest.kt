package com.maximerollin.aqualog

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
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
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
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
        val trendIds = runBlocking {
            val parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED).map {
                if (it.parameter == BuiltInParameter.TDS) it.copy(isActive = true) else it
            }
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = parameters,
            )
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
            val tds = setup.parameters.first { it.parameter == BuiltInParameter.TDS }
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
            setup.aquarium.id to tds.id
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
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Free trends show up to 90 days. Older data stays saved."))
        composeRule.onNodeWithText("Free trends show up to 90 days. Older data stays saved.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("pH"))
        composeRule.onNodeWithText("pH").performClick().assertIsSelected()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 7.2 pH"))
        composeRule.onNodeWithContentDescription("Exact value 7.2 pH").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        SystemClock.sleep(10)
        runBlocking {
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = trendIds.first,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    idempotencyKey = "trends-refresh-test",
                    measurementInputs = mapOf(trendIds.second to "100"),
                ),
            )
        }
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("TDS"))
        composeRule.onNodeWithText("TDS").performClick().assertIsSelected()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Indicative target 100–300 ppm · reference only, not a diagnosis"))
        composeRule.onNodeWithText("Indicative target 100–300 ppm · reference only, not a diagnosis")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 100 ppm"))
        composeRule.onNodeWithContentDescription("Exact value 100 ppm").assertIsDisplayed()
        scenario.close()
    }

    @Test
    fun proReadsCompleteHistory_comparesCompatibleParameters_andDowngradeKeepsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val oldTemperature = 20.5
        runBlocking {
            val parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED).map {
                if (it.parameter == BuiltInParameter.PHOSPHATE) it.copy(isActive = true) else it
            }
            val setup = application.aquariumRepository.createConfiguredAquarium(
                "Long history", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED, parameters,
            )
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            val nitrate = setup.parameters.first { it.parameter == BuiltInParameter.NITRATE }
            val phosphate = setup.parameters.first { it.parameter == BuiltInParameter.PHOSPHATE }
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis() - 4L * 365L * 86_400_000L,
                    "old-pro-point",
                    mapOf(temperature.id to oldTemperature.toString()),
                ),
            )
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis() - 12L * 86_400_000L,
                    "before-intervention",
                    mapOf(nitrate.id to "24"),
                ),
            )
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis() - 10L * 86_400_000L,
                    "pro-intervention",
                    mapOf(nitrate.id to "20", phosphate.id to "0.8"),
                    listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                ),
            )
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis() - 8L * 86_400_000L,
                    "after-intervention",
                    mapOf(nitrate.id to "18"),
                ),
            )
        }
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        var scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Trends").performClick()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Show complete history"))
        composeRule.onNodeWithText("Show complete history").performClick()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Advanced Pro analysis"))
        composeRule.onNodeWithText("Advanced Pro analysis").assertIsDisplayed()
        composeRule.onNodeWithText("Compatible Parameter comparison · mg/L").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("includes 1 intervention annotations", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Water change", substring = true))
        composeRule.onNode(hasText("Water change", substring = true) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(
            hasContentDescription("The center line marks the intervention", substring = true),
        )
        composeRule.onNodeWithContentDescription("The center line marks the intervention", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText(oldTemperature.toString(), substring = true))
        composeRule.onNodeWithText(oldTemperature.toString(), substring = true).assertIsDisplayed()

        application.revokePro()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Trends").performClick()
        composeRule.onNodeWithText("Free trends show up to 90 days. Older data stays saved.").assertIsDisplayed()
        assertTrue(
            runBlocking { application.aquariumRepository.observeTimeline(com.maximerollin.aqualog.shared.TimelineFilter()).first() }
                .any { it.measurements.any { measurement -> measurement.measurement.value == oldTemperature } },
        )
        scenario.close()
    }
}
