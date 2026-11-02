package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.TaskDate
import com.maximerollin.aqualog.shared.TaskInput
import com.maximerollin.aqualog.shared.TaskRecurrence
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiAquariumFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun server_entitled_user_can_create_select_edit_and_archive_a_second_aquarium() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                name = "Salon",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            waitForText("Salon")
            composeRule.onNodeWithText("More").performClick()
            waitForText("Pro active")
            composeRule.onNodeWithText("Add Aquarium").performScrollTo().performClick()
            waitForText("Aquarium name")
            composeRule.onNodeWithText("Aquarium name").performTextInput("Crevettes")
            composeRule.onNodeWithText("Volume").performTextInput("30")
            composeRule.onNodeWithText("Save Aquarium").performClick()
            waitForEditorClosed()

            waitForText("Crevettes")
            composeRule.onNodeWithText("Salon").assertIsDisplayed()
            composeRule.onNodeWithText("Edit Aquarium").performScrollTo().performClick()
            waitForText("Aquarium name")
            composeRule.onNodeWithText("Aquarium name").performTextClearance()
            composeRule.onNodeWithText("Aquarium name").performTextInput("Nano")
            composeRule.onNodeWithText("Save Aquarium").performClick()
            waitForEditorClosed()
            waitForText("Nano")
            composeRule.onNodeWithText("Archive Aquarium").performScrollTo().performClick()
            waitForText("Salon")

            runBlocking {
                val all = application.aquariumRepository.observeAquariums(includeArchived = true).first()
                check(all.size == 2 && all.single { it.name == "Nano" }.isArchived)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun swipingTheSceneMeasuresIntoTheShownAquarium() {
        // The seeded Session makes History on « Aqua 120 » non-empty; opening it first starts its observer there.
        twoAquariums(seedAquaSession = true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            composeRule.onNodeWithText("History").performClick()
            waitForText("Chronology")
            composeRule.onNodeWithText("Tank").performClick()
            composeRule.onNodeWithTag("home-pager").performTouchInput { swipeLeft() }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Nano 30")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Measure").performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
            composeRule.onNodeWithText("Save Session").performClick()
            waitForText("measured today", substring = true)
            composeRule.onNodeWithText("History").performClick()
            waitForText("Chronology")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Nano 30")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithContentDescription("Open Session details").assertCountEquals(1)
            composeRule.onNodeWithText("Aqua 120").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun alertBandOpensTheOtherAquarium() {
        val application = twoAquariums()
        runBlocking {
            val nano = application.aquariumRepository.observeAquariums().first().first { it.name == "Nano 30" }
            val yesterday = LocalDate.now().minusDays(1)
            application.aquariumRepository.createTask(
                TaskInput(
                    aquariumId = nano.id,
                    title = "Change filter wool",
                    recurrence = TaskRecurrence.ONCE,
                    firstDueDate = TaskDate(yesterday.year, yesterday.monthValue, yesterday.dayOfMonth),
                    minuteOfDay = null,
                    timeZoneId = ZoneId.systemDefault().id,
                ),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            composeRule.onNodeWithText("Nano 30").assertDoesNotExist()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Nano 30", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText("Nano 30", substring = true) and hasClickAction()).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("Nano 30")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Aqua 120").assertDoesNotExist()
        } finally {
            scenario.close()
        }
    }

    private fun twoAquariums(seedAquaSession: Boolean = false): TestAquaLogApplication {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val repository = application.aquariumRepository
            val aqua = repository.createConfiguredAquarium(
                name = "Aqua 120", volume = 120.0, volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            repository.createConfiguredAquarium(
                name = "Nano 30", volume = 30.0, volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
                access = AquariumCreationAccess.PRO,
            )
            repository.selectAquarium(aqua.aquarium.id)
            if (seedAquaSession) {
                repository.saveRapidSession(
                    RapidSessionInput(
                        aqua.aquarium.id, System.currentTimeMillis() - 60_000, "seed-aqua",
                        mapOf(aqua.parameters.first(ParameterDefinition::isActive).id to "23.0"),
                    ),
                )
            }
        }
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        return application
    }

    private fun waitForEditorClosed() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Aquarium name")).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
