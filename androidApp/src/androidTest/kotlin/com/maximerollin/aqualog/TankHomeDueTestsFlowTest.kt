package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.shared.plusDays
import com.maximerollin.aqualog.shared.assistance.NEVER_INTERVAL_DAYS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TankHomeDueTestsFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application: TestAquaLogApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication

    /** An Established Aquarium where only nitrate is due: measured 8 days ago (rhythm 7), the rest today. */
    private fun prepareNitrateDue(): String {
        application.resetRepository()
        return runBlocking {
            val repository = application.aquariumRepository
            val setup = repository.createConfiguredAquarium(
                "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val active = setup.parameters.filter { it.isActive }
            val nitrate = active.single { it.parameter == BuiltInParameter.NITRATE }
            val now = System.currentTimeMillis()
            repository.saveRapidSession(
                RapidSessionInput(setup.aquarium.id, now - 8 * 86_400_000L, "old", mapOf(nitrate.id to "10")),
            )
            val inRange = mapOf(
                BuiltInParameter.TEMPERATURE to "25", BuiltInParameter.PH to "7", BuiltInParameter.NITRITE to "0",
                BuiltInParameter.GH to "8", BuiltInParameter.KH to "5",
            )
            repository.saveRapidSession(
                RapidSessionInput(
                    setup.aquarium.id, now, "today",
                    (active - nitrate).associate { it.id to inRange.getValue(it.parameter) },
                ),
            )
            nitrate.id
        }
    }

    @Test
    fun dueNitrate_showsATestRow_thatOpensEntryWithNitrateFirst() {
        prepareNitrateDue()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Test NO₃")
            val today = LocalDate.now()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                TaskDate(today.year, today.monthValue, today.dayOfMonth) in
                    application.recordingTaskReminderScheduler.scheduledDueTestDates
            }
            composeRule.onNodeWithText("Test NO₃").performClick()
            waitForText("Measurements")
            val nitrate = composeRule.onNodeWithTag("rapid-measurement-nitrate")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { nitrate.assertIsFocused() }.isSuccess
            }
            assertTrue(
                nitrate.getBoundsInRoot().top < composeRule.onNodeWithTag("rapid-measurement-temperature").getBoundsInRoot().top,
            )
        } finally {
            scenario.close()
        }
    }

    @Test
    fun rhythmSetting_never_removesTheRow() {
        val nitrateId = prepareNitrateDue()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Test NO₃")
            composeRule.onNodeWithText("More").performClick()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Analysis rhythm"))
            composeRule.onNodeWithText("Analysis rhythm").performClick()
            waitForText("Nitrate")
            composeRule.onNodeWithText("Nitrate").performClick()
            composeRule.onNodeWithText("Never").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    application.aquariumRepository.observeCurrentSetup().first()
                        ?.parameters?.single { it.id == nitrateId }?.analysisIntervalDays == NEVER_INTERVAL_DAYS
                }
            }
            composeRule.onNodeWithText("Back").performClick()
            composeRule.onNodeWithText("Tank").performClick()
            waitForText("Measure")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Test NO₃")).fetchSemanticsNodes().isEmpty()
            }
            // pH (every 7 days, measured today) is now the next due test.
            val today = LocalDate.now()
            val inAWeek = TaskDate(today.year, today.monthValue, today.dayOfMonth).plusDays(7)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                application.recordingTaskReminderScheduler.scheduledDueTestDates.lastOrNull() == inAWeek
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun cycledProposal_switchesProfile_andLaterHidesItUntilTheNextSession() {
        application.resetRepository()
        val (setup, parametersBefore) = runBlocking {
            val repository = application.aquariumRepository
            val setup = repository.createConfiguredAquarium(
                "Cyclage", 60.0, VolumeUnit.LITERS, AquariumProfile.CYCLING,
                OnboardingPresets.parametersFor(AquariumProfile.CYCLING),
            )
            val zeros = setup.parameters.filter { it.parameter in listOf(BuiltInParameter.AMMONIA, BuiltInParameter.NITRITE) }
                .associate { it.id to "0" }
            repository.saveRapidSession(RapidSessionInput(setup.aquarium.id, System.currentTimeMillis() - 86_400_000L, "first", zeros))
            setup to repository.loadSetup(setup.aquarium.id)!!.parameters
        }
        val proposal = "Your tank looks cycled. Switch to the rhythm of an established tank?"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText(proposal)
            composeRule.onNodeWithText("Later").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodes(hasText(proposal)).fetchSemanticsNodes().isEmpty() }
            // A newer Session that still looks cycled brings the proposal back.
            runBlocking {
                val zeros = setup.parameters.filter { it.parameter in listOf(BuiltInParameter.AMMONIA, BuiltInParameter.NITRITE) }
                    .associate { it.id to "0" }
                application.aquariumRepository.saveRapidSession(
                    RapidSessionInput(setup.aquarium.id, System.currentTimeMillis(), "second", zeros),
                )
            }
            waitForText(proposal)
            composeRule.onNodeWithText("Switch to established").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodes(hasText(proposal)).fetchSemanticsNodes().isEmpty() }
            val after = runBlocking { application.aquariumRepository.loadSetup(setup.aquarium.id)!! }
            assertEquals(AquariumProfile.ESTABLISHED, after.aquarium.profile)
            assertEquals(parametersBefore, after.parameters)
        } finally {
            scenario.close()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
