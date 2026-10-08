package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.DatePicker
import android.widget.TimePicker
import com.maximerollin.aqualog.shared.AquariumProfile
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
    fun taskDateAndOptionalTime_areChosenWithTouchFriendlyPickers() {
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
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Tasks & reminders").performClick()
        composeRule.onNodeWithText("First due date").performClick()
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        pressBack()
        composeRule.onNodeWithText("Time (optional)").performClick()
        onView(isAssignableFrom(TimePicker::class.java)).check(matches(isDisplayed()))
        pressBack()

        scenario.close()
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
        composeRule.onNodeWithText("Task occurrence").assertIsDisplayed()
        composeRule.onNodeWithText("Change filter wool").assertIsDisplayed()
        composeRule.onNodeWithText("Session").assertIsDisplayed()
        val sessionTop = composeRule.onNodeWithText("Session").fetchSemanticsNode().boundsInRoot.top
        val taskTop = composeRule.onNodeWithText("Task occurrence").fetchSemanticsNode().boundsInRoot.top
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
        scenario.close()
    }
}
