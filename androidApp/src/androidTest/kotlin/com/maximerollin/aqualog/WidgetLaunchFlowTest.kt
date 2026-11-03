package com.maximerollin.aqualog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.TimelineFilter
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLaunchFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun widgetMeasure_forAnotherAquarium_opensEntryForThatAquarium() {
        val application = twoAquariums()
        val nanoId = aquariumId(application, "Nano 30")
        val scenario = ActivityScenario.launch<MainActivity>(widgetIntent(nanoId, WidgetTarget.MEASURE))
        try {
            waitForText("New Session")
            waitForText("Nano 30 · ", substring = true)
            composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
            composeRule.onNodeWithText("Save Session").performClick()
            waitForText("measured today", substring = true)
            runBlocking {
                val repository = application.aquariumRepository
                check(repository.observeTimeline(TimelineFilter(aquariumId = nanoId)).first().size == 1)
                check(repository.observeTimeline(TimelineFilter(aquariumId = aquariumId(application, "Aqua 120"))).first().isEmpty())
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun widgetMeasure_withADraftOpen_keepsTheDraft() {
        val application = twoAquariums()
        val nanoId = aquariumId(application, "Nano 30")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            composeRule.onNodeWithText("Measure").performClick()
            waitForText("Measurements")
            composeRule.onNodeWithContentDescription("Measurement for Temperature").performTextInput("24,5")
            // MainActivity is singleTask: this reaches the running instance through onNewIntent.
            scenario.onActivity { it.startActivity(widgetIntent(nanoId, WidgetTarget.MEASURE)) }
            // shortcut: a fixed pause, there is no UI signal for « nothing happened »; replace if the intent gets an ack.
            Thread.sleep(1_000)
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Measurement for Temperature").assertTextContains("24,5")
            waitForText("Aqua 120 · ", substring = true)
        } finally {
            finishByHand(scenario)
        }
    }

    @Test
    fun widgetMeasure_withGlanceActionData_opensEntryAndNoAuthError() {
        val application = twoAquariums()
        val nanoId = aquariumId(application, "Nano 30")
        // Glance's actionStartActivity sets a data URI of its own on the intent.
        val intent = widgetIntent(nanoId, WidgetTarget.MEASURE)
            .setData(Uri.parse("glance-action:/CALLBACK?appWidgetId=38&viewId=1&extraData=-1"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A cold start, as from the home screen; ActivityScenario cannot track an intent with data that the activity replaces.
        context.startActivity(intent)
        try {
            waitForText("New Session")
            waitForText("Nano 30 · ", substring = true)
            composeRule.onAllNodes(hasText("Authentication did not complete", substring = true)).assertCountEquals(0)
        } finally {
            var activity: Activity? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it is MainActivity }
                activity?.finish()
            }
            composeRule.waitUntil(timeoutMillis = 10_000) { activity?.isDestroyed != false }
        }
    }

    @Test
    fun widgetHome_fromHistory_landsOnTheAquariumHome() {
        val application = twoAquariums()
        val nanoId = aquariumId(application, "Nano 30")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            composeRule.onNodeWithText("History").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodes(hasText("See all")).fetchSemanticsNodes().isEmpty()
            }
            scenario.onActivity { it.startActivity(widgetIntent(nanoId, WidgetTarget.HOME)) }
            waitForText("See all")
            waitForText("Nano 30")
        } finally {
            finishByHand(scenario)
        }
    }

    @Test
    fun widgetMeasure_withTheTasksListOpen_opensEntryVisibly() {
        val application = twoAquariums()
        val aquaId = aquariumId(application, "Aqua 120")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            composeRule.onNode(hasText("See all") and hasClickAction()).performClick()
            waitForText("Tasks & reminders")
            scenario.onActivity { it.startActivity(widgetIntent(aquaId, WidgetTarget.MEASURE)) }
            waitForText("New Session")
            composeRule.onNodeWithContentDescription("Measurement for Temperature").assertIsDisplayed()
        } finally {
            finishByHand(scenario)
        }
    }

    @Test
    fun widgetPro_forAProUser_doesNotOpenThePaywall() {
        twoAquariums()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("Aqua 120")
            // shortcut: a fixed pause for the in-memory entitlement check to turn Pro ACTIVE; there is no UI signal on the home.
            Thread.sleep(1_000)
            scenario.onActivity { it.startActivity(widgetIntent(null, WidgetTarget.PRO)) }
            Thread.sleep(1_000)
            composeRule.waitForIdle()
            composeRule.onAllNodes(hasText("Choose Free or Pro")).assertCountEquals(0)
            composeRule.onNodeWithText("Aqua 120").assertIsDisplayed()
        } finally {
            finishByHand(scenario)
        }
    }

    @Test
    fun widgetPro_opensThePaywall() {
        twoAquariums(pro = false)
        val scenario = ActivityScenario.launch<MainActivity>(widgetIntent(null, WidgetTarget.PRO))
        try {
            waitForText("Choose Free or Pro")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun widgetHome_forAnArchivedAquarium_staysOnTheCurrentOne() {
        val application = twoAquariums()
        val nanoId = aquariumId(application, "Nano 30")
        runBlocking { application.aquariumRepository.archiveAquarium(nanoId) }
        val scenario = ActivityScenario.launch<MainActivity>(widgetIntent(nanoId, WidgetTarget.HOME))
        try {
            waitForText("Aqua 120")
            // shortcut: a fixed pause, there is no UI signal for « nothing happened ».
            Thread.sleep(1_000)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Aqua 120").assertIsDisplayed()
            composeRule.onAllNodesWithText("Nano 30").assertCountEquals(0)
        } finally {
            scenario.close()
        }
    }

    // The handled intent was cleared with setIntent, so the scenario no longer recognises the activity: finish it by hand.
    private fun finishByHand(scenario: ActivityScenario<MainActivity>) {
        var activity: MainActivity? = null
        scenario.onActivity { activity = it; it.finish() }
        composeRule.waitUntil(timeoutMillis = 10_000) { activity?.isDestroyed == true }
    }

    private fun widgetIntent(aquariumId: String?, target: WidgetTarget) =
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_AQUARIUM_ID, aquariumId)
            .putExtra(MainActivity.EXTRA_WIDGET_TARGET, target.name)

    private fun aquariumId(application: TestAquaLogApplication, name: String): String = runBlocking {
        application.aquariumRepository.observeAquariums(includeArchived = true).first().first { it.name == name }.id
    }

    private fun twoAquariums(pro: Boolean = true): TestAquaLogApplication {
        val application = context.applicationContext as TestAquaLogApplication
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
        }
        if (pro) application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        return application
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
