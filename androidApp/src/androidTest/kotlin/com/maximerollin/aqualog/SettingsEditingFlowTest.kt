package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.AquariumSetup
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What onboarding decided can be changed afterwards: the measured Parameters, and a Task created by mistake. */
@RunWith(AndroidJUnit4::class)
class SettingsEditingFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as TestAquaLogApplication

    @Test
    fun measuredParameters_areEditedFromMore_andSavedOnLeaving() {
        val setup = seedAquarium()
        val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.scrollAboveBar("settings-list", hasText("Measured parameters"))
            composeRule.onNodeWithText("Measured parameters").performClick()
            composeRule.onNodeWithTag("parameter-settings-list").assertIsDisplayed()
            capture("parameter-settings.png")

            composeRule.onNodeWithTag("parameter-settings-list").performScrollToNode(hasTestTag("parameter-row-ph"))
            composeRule.onNodeWithTag("parameter-row-ph").performClick()
            composeRule.onNodeWithTag("range-sheet").assertIsDisplayed()
            composeRule.onNode(hasContentDescription("Increase Indicative min")).performClick()
            capture("parameter-settings-range-sheet.png")
            // The row behind the sheet has the same switch: the one in the sheet is meant.
            composeRule.onNode(hasContentDescription("Enabled for pH") and hasAnyAncestor(hasTestTag("range-sheet"))).performClick()
            composeRule.onNodeWithText("Done").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("range-sheet")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("parameter-settings-back").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasTestTag("parameter-settings-list")).fetchSemanticsNodes().isEmpty()
            }

            val stored = runBlocking { application.aquariumRepository.loadSetup(setup.aquarium.id) }!!
                .parameters.first { it.id == ph.id }
            assertFalse("pH was switched off in the sheet", stored.isActive)
            assertTrue("the indicative minimum went up one step", stored.indicativeMinimum!! > ph.indicativeMinimum!!)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun aRecurringTask_isDeletedFromItsMenu_afterAConfirmation() {
        val setup = seedAquarium()
        runBlocking {
            application.aquariumRepository.createTask(
                TaskInput(setup.aquarium.id, "Feed twice", TaskRecurrence.DAILY, TaskDate(2026, 10, 7), null, "Europe/Paris"),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
            composeRule.onNodeWithContentDescription("More actions, Feed twice").performClick()
            capture("task-menu.png")
            composeRule.onNodeWithText("Delete Task").performClick()
            composeRule.onNodeWithText("Delete this Task?").assertIsDisplayed()
            capture("task-delete-confirmation.png")
            composeRule.onNodeWithText("Delete").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Feed twice")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("No pending Task.").assertIsDisplayed()
            assertEquals(
                0,
                runBlocking { application.aquariumRepository.observePendingTaskOccurrences().first() }.size,
            )
        } finally {
            scenario.close()
        }
    }

    private fun seedAquarium(): AquariumSetup {
        application.resetRepository()
        return runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        TestStorage().openOutputFile("settings-editing/$name").use { stream ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
