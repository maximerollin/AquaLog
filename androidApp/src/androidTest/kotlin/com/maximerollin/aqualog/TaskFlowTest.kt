package com.maximerollin.aqualog

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun taskDate_isChosenWithTouchFriendlyPicker() {
        val scenario = launchTaskScreen()
        try {
            composeRule.onNodeWithText("New Task").performClick()
            composeRule.onNodeWithText("First due date").performClick()
            composeRule.onNodeWithTag("task-date-picker").assertIsDisplayed()
            pressBack()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun optionalTaskTime_isChosenWithTouchFriendlyPicker() {
        val scenario = launchTaskScreen()
        try {
            composeRule.onNodeWithText("New Task").performClick()
            composeRule.onNodeWithText("Time (optional)").performClick()
            composeRule.onNodeWithTag("task-time-picker").assertIsDisplayed()
            pressBack()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun completingRecurringTask_reschedulesNextOccurrence_andShowsResolutionInChronology() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val firstOccurrence = runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.createTask(
                TaskInput(
                    aquariumId = setup.aquarium.id,
                    title = "Change filter wool",
                    recurrence = TaskRecurrence.WEEKLY,
                    firstDueDate = TaskDate(2026, 10, 7),
                    minuteOfDay = 18 * 60,
                    timeZoneId = "Europe/Paris",
                ),
            ).occurrence
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
            composeRule.onNodeWithText("Change filter wool").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Done, Change filter wool").performScrollTo().assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    application.aquariumRepository.observeResolvedTaskOccurrences().first()
                        .any { it.id == firstOccurrence.id }
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                application.recordingTaskReminderScheduler.canceledOccurrenceIds.contains(firstOccurrence.id)
            }
            runBlocking {
                application.aquariumRepository.saveRapidSession(
                    RapidSessionInput(
                        aquariumId = firstOccurrence.aquariumId,
                        occurredAtEpochMillis = System.currentTimeMillis() + 60_000L,
                        idempotencyKey = "newer-than-task-resolution",
                        observation = "Newer Session",
                    ),
                )
            }
            composeRule.onNodeWithTag("task-list").performScrollToIndex(0)
            composeRule.onNodeWithText("Back").performClick()
            composeRule.onNodeWithText("History").performClick()
            composeRule.onNode(hasText("Completed", substring = true)).assertIsDisplayed()
            composeRule.onNodeWithText("Change filter wool").assertIsDisplayed()
            composeRule.onNodeWithText("Session").assertIsDisplayed()
            val sessionTop = composeRule.onNodeWithText("Session").fetchSemanticsNode().boundsInRoot.top
            val taskTop = composeRule.onNode(hasText("Completed", substring = true)).fetchSemanticsNode().boundsInRoot.top
            assertTrue("Newer Session should appear above the older resolved Task", sessionTop < taskTop)

            runBlocking {
                val pending = application.aquariumRepository.observePendingTaskOccurrences().first()
                val resolved = application.aquariumRepository.observeResolvedTaskOccurrences().first()
                val timeline = application.aquariumRepository.observeTimeline(
                    com.maximerollin.aqualog.shared.TimelineFilter(),
                ).first()
                assertEquals(TaskDate(2026, 10, 14), pending.single().dueDate)
                assertTrue(application.recordingTaskReminderScheduler.scheduledOccurrenceIds.contains(pending.single().id))
                assertEquals(firstOccurrence.id, resolved.single().id)
                assertEquals(1, timeline.size)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun aDueTask_canBeCompletedFromTheHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val today = java.time.LocalDate.now()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.createTask(
                TaskInput(
                    aquariumId = setup.aquarium.id,
                    title = "Clean the glass",
                    recurrence = TaskRecurrence.ONCE,
                    firstDueDate = TaskDate(today.year, today.monthValue, today.dayOfMonth),
                    minuteOfDay = null,
                    timeZoneId = java.time.ZoneId.systemDefault().id,
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Clean the glass")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Done, Clean the glass").assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Clean the glass")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("No pending Task.").assertIsDisplayed()
            composeRule.onNodeWithText("History").performClick()
            composeRule.onNode(hasText("Completed", substring = true)).assertIsDisplayed()
            composeRule.onNodeWithText("Clean the glass").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun linkedWaterChange_tickedOnHome_recordsTheAction() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val today = java.time.LocalDate.now()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.createTask(
                TaskInput(
                    aquariumId = setup.aquarium.id,
                    title = "Weekly water change",
                    recurrence = TaskRecurrence.WEEKLY,
                    firstDueDate = TaskDate(today.year, today.monthValue, today.dayOfMonth),
                    minuteOfDay = null,
                    timeZoneId = java.time.ZoneId.systemDefault().id,
                    maintenanceActionType = MaintenanceActionType.WATER_CHANGE,
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Weekly water change")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Done, Weekly water change").assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { application.aquariumRepository.observeResolvedTaskOccurrences().first().isNotEmpty() }
            }
            composeRule.onNodeWithText("History").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Water change", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText("Water change", substring = true) and hasText("20", substring = true)).assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun linkedFutureTask_tickedOnTheTaskScreen_isResolved() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val inThreeDays = java.time.LocalDate.now().plusDays(3)
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.aquariumRepository.createTask(
                TaskInput(
                    aquariumId = setup.aquarium.id,
                    title = "Future water change",
                    recurrence = TaskRecurrence.WEEKLY,
                    firstDueDate = TaskDate(inThreeDays.year, inThreeDays.monthValue, inThreeDays.dayOfMonth),
                    minuteOfDay = null,
                    timeZoneId = java.time.ZoneId.systemDefault().id,
                    maintenanceActionType = MaintenanceActionType.WATER_CHANGE,
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
            composeRule.onNodeWithContentDescription("Done, Future water change").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { application.aquariumRepository.observeResolvedTaskOccurrences().first().isNotEmpty() }
            }
            runBlocking {
                val pending = application.aquariumRepository.observePendingTaskOccurrences().first().single()
                assertEquals(inThreeDays.plusDays(7).dayOfMonth, pending.dueDate.day)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun newTask_isCreatedInASheet_thatClosesAfterTheSave_andNeverOnAnInvalidForm() {
        val scenario = launchTaskScreen()
        try {
            composeRule.onNodeWithText("No pending Task.").assertIsDisplayed()
            composeRule.onNodeWithText("New Task").performClick()
            composeRule.onNodeWithText("Save Task").performClick()
            composeRule.onNodeWithText("Task title").assertIsDisplayed()
            composeRule.onNodeWithText("Task title").performTextInput("Clean the glass")
            composeRule.onNodeWithText("Save Task").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Task title")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText("Clean the glass").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun overflowMenu_postponesAndIgnoresAPendingTask() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val setup = application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            listOf("Rinse the filter", "Trim the plants").forEach { title ->
                application.aquariumRepository.createTask(
                    TaskInput(
                        aquariumId = setup.aquarium.id,
                        title = title,
                        recurrence = TaskRecurrence.ONCE,
                        firstDueDate = TaskDate(2026, 10, 7),
                        minuteOfDay = null,
                        timeZoneId = "Europe/Paris",
                    ),
                )
            }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
            composeRule.onNodeWithContentDescription("More actions, Rinse the filter").performClick()
            composeRule.onNodeWithText("+1 day").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    application.aquariumRepository.observePendingTaskOccurrences().first()
                        .any { it.title == "Rinse the filter" && it.dueDate == TaskDate(2026, 10, 8) }
                }
            }
            composeRule.onNodeWithContentDescription("More actions, Trim the plants").performClick()
            composeRule.onNodeWithText("Ignore").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText("Trim the plants")).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            scenario.close()
        }
    }

    private fun launchTaskScreen(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
        return ActivityScenario.launch<MainActivity>(MainActivity::class.java).also {
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
        }
    }
}
