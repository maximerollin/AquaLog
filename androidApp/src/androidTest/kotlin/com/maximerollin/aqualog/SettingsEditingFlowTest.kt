package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
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

    @Test
    fun aTask_isEditedFromItsMenu_inTheSheetThatCreatedIt() {
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
            composeRule.onNodeWithText("Edit").performClick()
            composeRule.onNodeWithText("Edit Task").assertIsDisplayed()
            composeRule.onNode(hasText("Feed twice") and hasSetTextAction()).performTextReplacement("Feed once")
            closeSoftKeyboard()
            capture("task-edit-sheet.png")
            composeRule.onNodeWithText("Weekly").performScrollTo().performClick()
            composeRule.onNodeWithText("Save Task").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Edit Task")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("Feed once").assertIsDisplayed()
            val pending = runBlocking { application.aquariumRepository.observePendingTaskOccurrences().first() }.single()
            assertEquals("Feed once", pending.title)
            assertEquals(
                TaskRecurrence.WEEKLY,
                runBlocking { application.aquariumRepository.observeTaskRecurrences().first() }.getValue(pending.taskId),
            )
        } finally {
            scenario.close()
        }
    }

    @Test
    fun anArchivedAquarium_isRestoredFromMore() {
        val setup = seedAquarium()
        runBlocking {
            val second = application.aquariumRepository.createConfiguredAquarium(
                "Nano", 30.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.archiveAquarium(second.aquarium.id)
            application.aquariumRepository.selectAquarium(setup.aquarium.id)
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.scrollAboveBar("settings-list", hasText("Archived Aquariums"))
            composeRule.onNodeWithText("Archived Aquariums").performClick()
            composeRule.onNodeWithTag("archived-aquariums").assertIsDisplayed()
            capture("archived-aquariums.png")
            composeRule.onNodeWithContentDescription("Restore Nano").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking { application.aquariumRepository.observeAquariums().first() }.size == 2
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun calculators_giveAVolume_aWaterChange_andADose() {
        seedAquarium()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.scrollAboveBar("settings-list", hasText("Calculators"))
            composeRule.onNodeWithText("Calculators").performClick()
            composeRule.onNodeWithTag("calculators-list").assertIsDisplayed()
            composeRule.onNode(hasText("Length (cm)") and hasSetTextAction()).performTextInput("80")
            composeRule.onNode(hasText("Width (cm)") and hasSetTextAction()).performTextInput("30")
            composeRule.onNode(hasText("Water height (cm)") and hasSetTextAction()).performTextInput("50")
            closeSoftKeyboard()
            composeRule.onNodeWithTag("calculator-volume-result").assertTextEquals("120 L · 31.7 US gal")
            capture("calculators.png")

            composeRule.onNodeWithTag("calculators-list").performScrollToNode(hasTestTag("calculator-water-change-result"))
            composeRule.onNode(hasText("Share changed (%)") and hasSetTextAction()).performTextInput("25")
            composeRule.onNode(hasText("Nitrate now (mg/L)") and hasSetTextAction()).performTextInput("40")
            closeSoftKeyboard()
            composeRule.onNodeWithTag("calculator-water-change-result")
                .assertTextEquals("To change: 30 L\nEstimated nitrate afterwards: 30 mg/L")

            composeRule.onNodeWithTag("calculators-list").performScrollToNode(hasTestTag("calculator-dose-result"))
            composeRule.onNode(hasText("Dose on the label") and hasSetTextAction()).performTextInput("5")
            composeRule.onNode(hasText("For a volume of (L)") and hasSetTextAction()).performTextInput("50")
            closeSoftKeyboard()
            composeRule.onNodeWithTag("calculator-dose-result").assertTextEquals("For this tank: 12, in the unit of the label")
            composeRule.onNodeWithTag("calculators-back").performClick()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun aSessionWithATypedValue_asksBeforeBeingDiscarded() {
        seedAquarium()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasLabel("Measure")).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("24.5")
            closeSoftKeyboard()
            pressBack()
            composeRule.onNodeWithText("Discard this Session?").assertIsDisplayed()
            capture("session-discard.png")
            composeRule.onNodeWithText("Keep editing").performClick()
            composeRule.onAllNodes(hasText("Discard this Session?")).assertCountEquals(0)
            pressBack()
            composeRule.onNodeWithText("Discard").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasLabel("More")).fetchSemanticsNodes().isNotEmpty()
            }
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
