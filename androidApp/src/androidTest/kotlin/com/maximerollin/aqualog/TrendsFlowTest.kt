package com.maximerollin.aqualog

import android.os.SystemClock
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
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
import java.time.LocalDate
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

        trendsTab().performClick()
        composeRule.onNodeWithText("30 days").assertIsSelected()
        composeRule.onNodeWithText("7 days").assertIsDisplayed()
        composeRule.onNodeWithText("90 days").assertIsDisplayed()
        // The hero card shows the parameter to watch (Temperature, the first with a reading); the others are rows.
        composeRule.onNodeWithText("24.5", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("7.2", substring = true).assertIsDisplayed()
        val chartDescription = "Temperature trend chart · solid line and circle point markers · " +
            "hatched indicative range · diamond Event markers"
        composeRule.onNodeWithContentDescription(chartDescription).assertIsDisplayed()
        // The exact values stay folded: only the hero's button shows, collapsed.
        composeRule.onAllNodesWithText("Exact values").assertCountEquals(1)
        composeRule.onNode(
            hasText("Exact values") and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Exact value 24.5 °C").assertCountEquals(0)
        // TalkBack reads the merged description instead of the Text: it must carry the reading and the name.
        composeRule.onNode(
            hasTestTag("trend-row") and hasContentDescription("7.2, pH") and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"),
        ).assertIsDisplayed()
        // The hero's parameter is not repeated as a row, down to the last one.
        composeRule.onAllNodes(hasTestTag("trend-row") and hasText("Temperature")).assertCountEquals(0)
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Shaded band: indicative range · ▲ Water changes"))
        composeRule.onAllNodes(hasTestTag("trend-row") and hasText("Temperature")).assertCountEquals(0)
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Exact values") and hasClickAction())

        // The hero's button opens its exact values and Events in place.
        composeRule.onNode(hasText("Exact values") and hasClickAction()).performClick()
        composeRule.onNode(
            hasText("Exact values") and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"),
        ).assertIsDisplayed()
        // The hero keeps its disclaimer and its chart once opened.
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("▲ Water change · Reference, not a diagnosis"))
        composeRule.onNodeWithText("▲ Water change · Reference, not a diagnosis").assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription(chartDescription))
        composeRule.onNodeWithContentDescription(chartDescription).assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 24.5 °C"))
        composeRule.onNodeWithContentDescription("Exact value 24.5 °C").assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Water change · Incident: Heater stopped", substring = true))
        composeRule.onNodeWithText("Water change · Incident: Heater stopped", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Free trends show up to 90 days. Older data stays saved."))
        composeRule.onNodeWithText("Free trends show up to 90 days. Older data stays saved.")
            .assertIsDisplayed()

        // Only one is expanded at a time: opening the pH row folds the hero's values back, and unfolds its own.
        composeRule.scrollAboveBar("trend-list", hasTestTag("trend-row") and hasContentDescription("7.2, pH"))
        parameterRow("pH").performClick()
        composeRule.onAllNodesWithContentDescription("Exact value 24.5 °C").assertCountEquals(0)
        composeRule.onNode(
            hasTestTag("trend-row") and hasContentDescription("7.2, pH") and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"),
        ).assertIsDisplayed()
        val phTarget = "Indicative range 6.5–7.5 pH · reference only, not a diagnosis"
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText(phTarget))
        composeRule.onNodeWithText(phTarget).assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasContentDescription("Exact value 7.2 pH"))
        composeRule.onNodeWithContentDescription("Exact value 7.2 pH").assertIsDisplayed()

        composeRule.onNode(hasLabel("Tank")).performClick()
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
        trendsTab().performClick()
        composeRule.scrollAboveBar("trend-list", hasText("TDS") and hasClickAction())
        parameterRow("TDS").performClick()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Indicative range 100–300 ppm · reference only, not a diagnosis"))
        composeRule.onNodeWithText("Indicative range 100–300 ppm · reference only, not a diagnosis")
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
        var interventionSessionId = ""
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
            interventionSessionId = application.aquariumRepository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id,
                    System.currentTimeMillis() - 10L * 86_400_000L,
                    "pro-intervention",
                    mapOf(nitrate.id to "20", phosphate.id to "0.8"),
                    listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                ),
            ).session.id
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

        trendsTab().performClick()
        // Pro's complete history is behind the header's Custom period button, in its sheet.
        composeRule.onNode(hasLabel("Custom period")).performClick()
        composeRule.onNodeWithText("Show complete history").performClick()
        // The complete history is no period: none reads selected and the subtitle names the window.
        composeRule.onNodeWithText("Long history · Complete history").assertIsDisplayed()
        composeRule.onAllNodes(periodRadio()).assertCountEquals(3)
        composeRule.onAllNodes(periodRadio() and isSelected()).assertCountEquals(0)
        composeRule.onNodeWithTag("trend-list").performScrollToNode(hasText("Advanced Pro analysis"))
        composeRule.onNodeWithText("Advanced Pro analysis").assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Compatible Parameter comparison · mg/L"))
        composeRule.onNodeWithText("Compatible Parameter comparison · mg/L").assertIsDisplayed()
        composeRule.onNodeWithTag("trend-list").performScrollToNode(
            hasContentDescription("includes 1 intervention annotations", substring = true),
        )
        composeRule.onNodeWithContentDescription("includes 1 intervention annotations", substring = true)
            .assertIsDisplayed()
        val interventionTag = "trend-intervention-$interventionSessionId"
        composeRule.scrollAboveBar("trend-list", hasTestTag(interventionTag))
        composeRule.onNodeWithTag(interventionTag).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(
                hasContentDescription("The center line marks the intervention", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
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
        trendsTab().performClick()
        composeRule.onNodeWithTag("trend-list")
            .performScrollToNode(hasText("Free trends show up to 90 days. Older data stays saved."))
        composeRule.onNodeWithText("Free trends show up to 90 days. Older data stays saved.").assertIsDisplayed()
        assertTrue(
            runBlocking { application.aquariumRepository.observeTimeline(com.maximerollin.aqualog.shared.TimelineFilter()).first() }
                .any { it.measurements.any { measurement -> measurement.measurement.value == oldTemperature } },
        )
        scenario.close()
    }

    @Test
    fun freeUser_customPeriodOpensThePaywall_notTheCompleteHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                "Free tank", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        trendsTab().performClick()
        // No Session yet: no hero, the empty card, and the rows show « — ».
        val noMeasurements = "No Measurements for this Parameter in the selected period."
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(noMeasurements).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(noMeasurements).assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("trend-hero-value"), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("trend-hero-state"), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Exact values").assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("trend-row") and hasText("—")).onFirst().assertIsDisplayed()
        composeRule.awaitLabel("Custom period")
        composeRule.onNode(hasLabel("Custom period")).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("All your tanks, their whole story.")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("All your tanks, their whole story.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Show complete history").assertCountEquals(0)
        scenario.close()
    }

    @Test
    fun proCustomPeriod_rejectsAnUnorderedRange_thenAppliesAValidOne() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                "Custom tank", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            application.aquariumRepository.saveRapidSession(
                RapidSessionInput(setup.aquarium.id, System.currentTimeMillis(), "custom-period", mapOf(temperature.id to "24.5")),
            )
        }
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val today = LocalDate.now()

        trendsTab().performClick()
        composeRule.awaitLabel("Custom period")
        composeRule.onNode(hasLabel("Custom period")).performClick()
        // Dates in the shown format (YYYY-MM-DD), but the end before the start: the error shows and the sheet stays.
        composeRule.onNodeWithText("Start date").performTextReplacement(today.toString())
        composeRule.onNodeWithText("End date").performTextReplacement(today.minusDays(30).toString())
        composeRule.onNodeWithText("Apply custom period").performClick()
        composeRule.onNodeWithText("Enter an ordered start and end date as YYYY-MM-DD.").assertIsDisplayed()
        composeRule.onNodeWithText("Apply custom period").assertIsDisplayed()

        // An ordered range closes the sheet; the subtitle names the window and no period reads selected.
        composeRule.onNodeWithText("Start date").performTextReplacement(today.minusDays(30).toString())
        composeRule.onNodeWithText("End date").performTextReplacement(today.toString())
        composeRule.onNodeWithText("Apply custom period").performClick()
        composeRule.onAllNodesWithText("Apply custom period").assertCountEquals(0)
        composeRule.onNodeWithText("Custom tank · Custom period").assertIsDisplayed()
        composeRule.onAllNodes(periodRadio() and isSelected()).assertCountEquals(0)
        composeRule.onNodeWithText("24.5", substring = true).assertIsDisplayed()

        // Tapping a period returns to it.
        composeRule.onNode(periodRadio() and hasText("30 days")).performClick().assertIsSelected()
        composeRule.onNodeWithText("Custom tank").assertIsDisplayed()
        composeRule.onNode(periodRadio() and hasText("7 days")).assertIsNotSelected()
        scenario.close()
    }

    private fun parameterRow(label: String) = composeRule.onNode(hasClickAction() and hasText(label))

    private fun periodRadio() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    // Target the Trends tab by its role.
    private fun trendsTab() = composeRule.onNode(
        hasLabel("Trends") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
    )
}
