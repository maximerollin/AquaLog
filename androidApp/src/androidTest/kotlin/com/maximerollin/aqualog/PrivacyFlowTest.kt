package com.maximerollin.aqualog

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class PrivacyFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun voluntaryFeedback_explainsItsPayload_andNeverEntersProductTelemetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                name = "Private Aquarium",
                volume = 80.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNodeWithText("Settings").performClick()
            composeRule.onNode(hasText("Send feedback") and hasClickAction())
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText(
                "Only the message you type below is submitted. AquaLog does not attach water values",
                substring = true,
            )
            val privateMessage = "My pH note for Private Aquarium is 7.2"
            composeRule.onNodeWithText("Feedback message").performTextInput(privateMessage)
            composeRule.onNode(hasText("Send feedback") and hasClickAction()).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                application.recordedFeedback.any { it.message == privateMessage }
            }

            assertEquals(listOf(privateMessage), application.recordedFeedback.map { it.message })
            assertTrue(
                application.analyticsPayloads.none { payload ->
                    privateMessage in payload.toString() ||
                        payload.properties.keys.any {
                            it in setOf("water_value", "note", "observation", "aquarium_name", "email", "photo")
                        }
                },
            )
        } finally {
            scenario.close()
        }
    }
}
