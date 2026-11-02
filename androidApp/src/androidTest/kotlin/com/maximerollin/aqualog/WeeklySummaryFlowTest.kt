package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
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
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.TaskResolution
import com.maximerollin.aqualog.shared.TaskResolutionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklySummaryFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application: TestAquaLogApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication

    private val zone = ZoneId.systemDefault()

    /** The last Sunday before today, so every seeded moment is in the past. */
    private val sunday: LocalDate = LocalDate.now(zone).let { it.minusDays(it.dayOfWeek.value.toLong()) }

    private fun noon(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * The week ending [sunday]: nitrate 20 the Saturday before, then every parameter on Wednesday with nitrate at 35,
     * and one Tâche completed that Wednesday. The clock is set to [clockDay] at noon.
     */
    private fun prepareWeek(clockDay: LocalDate = sunday) {
        application.resetRepository()
        application.clockMillis = noon(clockDay)
        runBlocking {
            val repository = application.aquariumRepository
            val setup = repository.createConfiguredAquarium(
                "Amazonien", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val active = setup.parameters.filter { it.isActive }
            val nitrate = active.single { it.parameter == BuiltInParameter.NITRATE }
            repository.saveRapidSession(RapidSessionInput(setup.aquarium.id, noon(sunday.minusDays(8)), "before", mapOf(nitrate.id to "20")))
            val values = mapOf(
                BuiltInParameter.TEMPERATURE to "25", BuiltInParameter.PH to "7", BuiltInParameter.NITRITE to "0",
                BuiltInParameter.NITRATE to "35", BuiltInParameter.GH to "8", BuiltInParameter.KH to "5",
            )
            val wednesday = sunday.minusDays(4)
            repository.saveRapidSession(
                RapidSessionInput(setup.aquarium.id, noon(wednesday), "week", active.associate { it.id to values.getValue(it.parameter) }),
            )
            val task = repository.createTask(
                TaskInput(
                    setup.aquarium.id, "Water change", TaskRecurrence.ONCE,
                    TaskDate(wednesday.year, wednesday.monthValue, wednesday.dayOfMonth), null, zone.id,
                ),
            )
            repository.resolveTaskOccurrence(TaskResolutionInput(task.occurrence.id, TaskResolution.COMPLETED, noon(wednesday)))
        }
    }

    @After
    fun resetClock() {
        application.clockMillis = null
    }

    @Test
    fun summaryCard_onSunday_showsFreePart_andProPartForPro() {
        prepareWeek()
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            showCard()
            composeRule.onNodeWithText("6 tests done · 1 task completed").assertIsDisplayed()
            scrollTo("NO₃ 20 → 35, rising")
            scrollTo("Next week: test pH and NO₃")
            assertAbsent("Value changes and next week's tests with Pro")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun summaryCard_forFree_invitesToPro_onMondayToo() {
        prepareWeek(clockDay = sunday.plusDays(1))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            showCard()
            composeRule.onNodeWithText("6 tests done · 1 task completed").assertIsDisplayed()
            assertAbsent("NO₃ 20 → 35, rising")
            scrollTo("Value changes and next week's tests with Pro")
            composeRule.onNodeWithText("Value changes and next week's tests with Pro").performClick()
            waitForText("Choose Free or Pro")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun summaryCard_isHidden_fromTuesday() {
        prepareWeek(clockDay = sunday.plusDays(2))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Measure")
            assertAbsent("Weekly summary")
        } finally {
            scenario.close()
        }
    }

    private fun showCard() {
        waitForText("Measure")
        composeRule.onNodeWithTag("home-list").performScrollToNode(hasText("Weekly summary"))
        waitForText("6 tests done · 1 task completed")
    }

    private fun scrollTo(text: String) {
        waitForText(text)
        composeRule.onNodeWithTag("home-list").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        composeRule.waitForIdle()
        check(composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()) { "$text is shown" }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
