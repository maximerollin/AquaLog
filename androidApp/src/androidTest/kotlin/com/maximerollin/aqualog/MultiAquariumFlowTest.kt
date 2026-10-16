package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

        waitForText("Salon")
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Pro active")
        composeRule.onNodeWithText("Add Aquarium").performClick()
        composeRule.onNodeWithText("Aquarium name").performTextInput("Crevettes")
        composeRule.onNodeWithText("Volume").performTextInput("30")
        composeRule.onNodeWithText("Save Aquarium").performClick()

        waitForText("Crevettes")
        composeRule.onNodeWithText("Salon").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Aquarium").performClick()
        composeRule.onNodeWithText("Aquarium name").performTextClearance()
        composeRule.onNodeWithText("Aquarium name").performTextInput("Nano")
        composeRule.onNodeWithText("Save Aquarium").performClick()
        waitForText("Nano")
        composeRule.onNodeWithText("Archive Aquarium").performClick()
        waitForText("Salon")

        runBlocking {
            val all = application.aquariumRepository.observeAquariums(includeArchived = true).first()
            check(all.size == 2 && all.single { it.name == "Nano" }.isArchived)
        }
        scenario.close()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
