package com.maximerollin.aqualog

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

/** The whole onboarding on a device, in English: profile, aquarium, Parameters (range sheet, reorder), practice, offer. */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private fun rowText(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun walksFromWelcomeToTheOffer_andSavesTheEditedParametersInOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val presets = OnboardingPresets.parametersFor(AquariumProfile.PLANTED_SHRIMP)
        val presetPh = presets.first { it.parameter == BuiltInParameter.PH }
        val presetOrder = presets.sortedBy { it.position }.map { it.parameter }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNodeWithText("Get started").performClick()
            composeRule.onNodeWithText("Planted / shrimp").performClick()
            composeRule.onNodeWithText("Continue to Aquarium").performClick()

            composeRule.onNodeWithTag("aquarium-name").performTextInput("Salon")
            composeRule.onNodeWithTag("aquarium-volume").performTextInput("120")
            composeRule.onNodeWithTag("onboarding-aquarium-live").assertTextContains("Salon", substring = true)
            composeRule.onNodeWithText("Continue to Parameters").performClick()

            val pillBefore = rowText("parameter-row-ph")
            composeRule.onNodeWithTag("parameter-row-ph").performScrollTo().performClick()
            composeRule.onNodeWithTag("range-sheet").assertIsDisplayed()
            composeRule.onNode(hasContentDescription("Increase Indicative min")).performClick()
            composeRule.onNodeWithText("Done").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(hasTestTag("range-sheet")).fetchSemanticsNodes().isEmpty()
            }
            assertNotEquals("the pH pill shows the new minimum", pillBefore, rowText("parameter-row-ph"))

            composeRule.onNodeWithTag("parameter-row-ph").performCustomAccessibilityActionWithLabel("Move pH down")
            composeRule.onNodeWithText("Try a mini Session").performClick()

            val first = presets.filter { it.isActive }.minBy { it.position }.parameter.storageValue
            composeRule.onNodeWithTag("practice-$first").performTextInput("7.2")
            composeRule.onNodeWithText("See Free and Pro").performClick()

            composeRule.onNodeWithTag("onboarding-list").performScrollToNode(hasTestTag("plan-annual"))
            composeRule.onNodeWithTag("plan-annual").assertIsSelected()
            composeRule.onNodeWithTag("choose-pro").assertIsDisplayed()
            composeRule.onNodeWithText("Continue for free").performClick()

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(hasText("Salon")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Salon").assertIsDisplayed()

            val setup = runBlocking { application.aquariumRepository.observeCurrentSetup().first() }!!
            val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
            val step = 10.0.pow(-presetPh.precision)
            assertEquals(presetPh.indicativeMinimum!! + step, ph.indicativeMinimum!!, 1e-9)
            val savedOrder = setup.parameters.sortedBy { it.position }.map { it.parameter }
            val phIndex = presetOrder.indexOf(BuiltInParameter.PH)
            val expected = presetOrder.toMutableList().apply {
                removeAt(phIndex)
                add(phIndex + 1, BuiltInParameter.PH)
            }
            assertEquals(expected, savedOrder)
        } finally {
            scenario.close()
        }
    }
}
