package com.maximerollin.aqualog

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.resources.*
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.BuiltInParameter
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddInhabitantFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val strings = LocalizedStrings()

    @Test
    fun searchOto_opensAGoodFiche_addsSix_andTheSheetRemovesTwo() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val aquariumId = runBlocking {
            val repository = application.aquariumRepository
            val setup = repository.createConfiguredAquarium(
                "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val ph = setup.parameters.single { it.isActive && it.parameter == BuiltInParameter.PH }
            repository.saveRapidSession(RapidSessionInput(setup.aquarium.id, System.currentTimeMillis(), "ph", mapOf(ph.id to "7.2")))
            setup.aquarium.id
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(5_000) { exists(strings.getString(Res.string.navigation_more)) }
            composeRule.onNodeWithText(strings.getString(Res.string.navigation_more)).performClick()
            val habitants = strings.getString(Res.string.habitants_title)
            composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText(habitants))
            composeRule.onNodeWithText(habitants).performClick()

            composeRule.onNodeWithText(strings.getString(Res.string.habitants_search_hint)).performTextInput("oto")
            composeRule.waitUntil(5_000) { exists("Otocinclus vittatus") }
            // The only result sits under « Vont bien dans ce bac », with the compatible badge.
            composeRule.onNodeWithText(strings.getString(Res.string.habitants_group_good)).assertExists()
            composeRule.onNodeWithText(strings.getString(Res.string.habitants_badge_good)).assertExists()
            composeRule.onNodeWithText("Otocinclus vittatus").performClick()

            composeRule.onNodeWithText("6").assertExists()
            val addButton = strings.getString(Res.string.habitants_add_button, 6, "Otocinclus")
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(addButton))
            composeRule.onNodeWithText(addButton).performClick()

            // The home comes back; tapping the scene opens the habitants sheet, which lists the six.
            composeRule.waitUntil(5_000) { !exists(addButton) && exists(strings.getString(Res.string.navigation_history)) }
            composeRule.onNodeWithContentDescription(strings.getString(Res.string.habitants_title)).performClick()
            composeRule.waitUntil(5_000) { exists(strings.getString(Res.string.habitants_present_group, "Otocinclus", 6)) }

            // « Retirer… » 2 of them leaves 4.
            composeRule.onNodeWithText(strings.getString(Res.string.habitants_remove)).performClick()
            composeRule.onNodeWithContentDescription(strings.getString(Res.string.habitants_count_increase)).performClick()
            composeRule.onNodeWithText(strings.getString(Res.string.habitants_remove_confirm, 2)).performClick()
            composeRule.waitUntil(5_000) { exists(strings.getString(Res.string.habitants_present_group, "Otocinclus", 4)) }
            pressBack()

            // The Chronologie shows the add and the removal.
            composeRule.waitUntil(5_000) { exists(strings.getString(Res.string.navigation_history)) }
            composeRule.onNodeWithText(strings.getString(Res.string.navigation_history)).performClick()
            composeRule.waitUntil(5_000) {
                exists(strings.getString(Res.string.timeline_inhabitant_added, "Otocinclus", 6)) &&
                    exists(strings.getString(Res.string.timeline_inhabitant_removed, "Otocinclus", 2))
            }
            val present = runBlocking { application.aquariumRepository.observeInhabitants(aquariumId).first() }
            assertEquals(listOf("otocinclus" to 4), present.map { it.speciesId to it.count })
        } finally {
            scenario.close()
        }
    }

    private fun exists(text: String) = composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
