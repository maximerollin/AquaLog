package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
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

        composeRule.onNodeWithText("Tasks & reminders").performClick()
        composeRule.onNodeWithText("Change filter wool").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                application.aquariumRepository.observeResolvedTaskOccurrences().first()
                    .any { it.id == firstOccurrence.id }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            application.recordingTaskReminderScheduler.canceledOccurrenceIds.contains(firstOccurrence.id)
        }
        composeRule.onNodeWithTag("task-list").performScrollToIndex(0)
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("Task occurrence").assertIsDisplayed()
        composeRule.onNodeWithText("Change filter wool").assertIsDisplayed()

        runBlocking {
            val pending = application.aquariumRepository.observePendingTaskOccurrences().first()
            val resolved = application.aquariumRepository.observeResolvedTaskOccurrences().first()
            val timeline = application.aquariumRepository.observeTimeline(
                com.maximerollin.aqualog.shared.TimelineFilter(),
            ).first()
            assertEquals(TaskDate(2026, 10, 14), pending.single().dueDate)
            assertTrue(application.recordingTaskReminderScheduler.scheduledOccurrenceIds.contains(pending.single().id))
            assertEquals(firstOccurrence.id, resolved.single().id)
            assertTrue(timeline.isEmpty())
        }
        scenario.close()
    }
}
