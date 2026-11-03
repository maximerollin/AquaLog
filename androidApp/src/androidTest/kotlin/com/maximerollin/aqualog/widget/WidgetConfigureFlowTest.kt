package com.maximerollin.aqualog.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.TestAquaLogApplication
import com.maximerollin.aqualog.assertMinimumTouchTarget
import com.maximerollin.aqualog.shared.AquariumCreationAccess
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetConfigureFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host by lazy { AppWidgetHost(context, 4242) }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Before
    fun allocate() {
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking {
            val repository = application.aquariumRepository
            repository.createConfiguredAquarium(
                name = "Aqua 120", volume = 120.0, volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            repository.createConfiguredAquarium(
                name = "Nano", volume = 30.0, volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED, parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
                access = AquariumCreationAccess.PRO,
            )
        }
        widgetId = host.allocateAppWidgetId()
        // Only a widget bound to one of our providers has a Glance id; binding needs a permission the shell holds.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            check(
                AppWidgetManager.getInstance(context)
                    .bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, SmallTankWidgetReceiver::class.java)),
            ) { "could not bind the test widget" }
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    @After
    fun release() = host.deleteAppWidgetId(widgetId)

    @Test
    fun pickingAnAquarium_pinsItAndReturnsOk() {
        val scenario = launch()
        try {
            waitForText("Nano")
            composeRule.onNodeWithText("Nano").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(widgetId, result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            val nanoId = runBlocking {
                (context.applicationContext as TestAquaLogApplication).aquariumRepository
                    .observeAquariums().first().first { it.name == "Nano" }.id
            }
            val pinned = runBlocking { getAppWidgetState(context, PreferencesGlanceStateDefinition, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId))[PINNED_AQUARIUM_ID] }
            assertEquals(nanoId, pinned)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backWithoutPicking_returnsCanceled() {
        val scenario = launch()
        try {
            waitForText("Nano")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(timeoutMillis = 10_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun rows_areAtLeast48dpAndNamed() {
        val scenario = launch()
        try {
            waitForText("Choose the Aquarium")
            for (name in listOf("Aqua 120", "Nano")) {
                waitForText(name)
                assertMinimumTouchTarget(
                    composeRule.onNode(hasText(name) and hasClickAction()).fetchSemanticsNode().boundsInRoot,
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun launch(): ActivityScenario<WidgetConfigureActivity> = ActivityScenario.launchActivityForResult(
        Intent(context, WidgetConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
    )

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
