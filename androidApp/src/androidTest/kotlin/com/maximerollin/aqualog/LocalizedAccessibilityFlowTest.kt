package com.maximerollin.aqualog

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedAccessibilityFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val uiModeManager = context.getSystemService(UiModeManager::class.java)

    @After
    fun restoreDeviceConfiguration() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
    }

    @Test
    fun criticalOnboardingScreens_haveLocalizedCaptures_andAccessiblePrimaryActions() {
        val languages = listOf("en", "fr", "de", "it", "es")
        val modes = listOf("light" to UiModeManager.MODE_NIGHT_NO, "dark-large" to UiModeManager.MODE_NIGHT_YES)
        languages.forEach { language ->
            modes.forEach { (mode, nightMode) ->
                localeManager.applicationLocales = LocaleList.forLanguageTags(language)
                uiModeManager.setApplicationNightMode(nightMode)
                instrumentation.uiAutomation.executeShellCommand(
                    "settings put system font_scale ${if (mode == "dark-large") "1.5" else "1.0"}",
                ).close()
                (context.applicationContext as TestAquaLogApplication).resetRepository()
                ActivityScenario.launch(MainActivity::class.java).use {
                    val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                        setLocale(LocaleList.forLanguageTags(language)[0])
                    })
                    val welcome = localized.getString(R.string.welcome_title)
                    val start = localized.getString(R.string.get_started)
                    awaitText(start)
                    compose.onNodeWithText(welcome).assertIsDisplayed()
                    assertMinimumTouchTarget(compose.onNode(hasText(start) and hasClickAction()).fetchSemanticsNode().boundsInRoot)
                    capture("welcome-$language-$mode.png")

                    compose.onNodeWithText(start).performClick()
                    val profile = localized.getString(R.string.profile_title)
                    val continueLabel = localized.getString(R.string.continue_to_aquarium)
                    awaitText(continueLabel)
                    compose.onNodeWithText(profile).assertIsDisplayed()
                    val continueAction = compose.onNode(hasText(continueLabel) and hasClickAction())
                    continueAction.performScrollTo().assertIsDisplayed()
                    assertMinimumTouchTarget(continueAction.fetchSemanticsNode().boundsInRoot)
                    capture("profile-$language-$mode.png")
                }
            }
        }
    }

    @Test
    fun configuredCriticalScreens_haveFiveLocaleSmokeCaptures_andDarkLargeCoverage() {
        val configurations = listOf("en", "fr", "de", "it", "es").map { Triple(it, "light", false) } +
            Triple("en", "dark-large", true)
        configurations.forEach { (language, mode, darkLarge) ->
            configure(language, darkLarge)
            val application = context.applicationContext as TestAquaLogApplication
            application.resetRepository()
            runBlocking {
                val setup = application.aquariumRepository.createConfiguredAquarium(
                    name = "Aqua 120",
                    volume = 120.5,
                    volumeUnit = VolumeUnit.LITERS,
                    profile = AquariumProfile.ESTABLISHED,
                    parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
                )
                val parameter = setup.parameters.first(ParameterDefinition::isActive)
                application.aquariumRepository.saveRapidSession(
                    RapidSessionInput(
                        aquariumId = setup.aquarium.id,
                        occurredAtEpochMillis = System.currentTimeMillis() - 86_400_000L,
                        idempotencyKey = "localized-$language-$mode",
                        measurementInputs = mapOf(parameter.id to "24.5"),
                        maintenanceActions = listOf(
                            MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                        ),
                        observation = "OK",
                    ),
                )
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                val localized = localizedContext(language)
                val newSession = localized.getString(R.string.new_session)
                awaitText(newSession)
                assertAction(newSession)
                captureAndInspect("home-$language-$mode.png")

                compose.onNodeWithText(newSession).performClick()
                val saveSession = localized.getString(R.string.save_session)
                awaitText(saveSession)
                assertAction(saveSession)
                compose.onNodeWithText(localized.getString(R.string.measurements_heading)).assertIsDisplayed()
                captureAndInspect("rapid-session-$language-$mode.png")
                compose.onNodeWithTag("rapid-session-list").performScrollToIndex(0)
                compose.onNodeWithText(localized.getString(R.string.cancel)).performClick()

                val tasks = localized.getString(R.string.open_tasks)
                compose.onNodeWithText(tasks).performClick()
                awaitText(localized.getString(R.string.tasks_title))
                compose.onNodeWithTag("task-list").performScrollToNode(
                    hasText(localized.getString(R.string.save_task)),
                )
                compose.onNode(
                    hasText(localized.getString(R.string.save_task)) and hasClickAction(),
                ).performScrollTo()
                assertAction(localized.getString(R.string.save_task))
                captureAndInspect("tasks-$language-$mode.png")
                compose.onNodeWithTag("task-list").performScrollToNode(hasText(localized.getString(R.string.back)))
                compose.onNodeWithText(localized.getString(R.string.back)).performClick()

                compose.onNodeWithText(localized.getString(R.string.navigation_history)).performClick()
                awaitText(localized.getString(R.string.timeline_title))
                captureAndInspect("timeline-$language-$mode.png")
                compose.onNodeWithText(localized.getString(R.string.trends_title)).performClick()
                compose.onNodeWithTag("trend-list").performScrollToNode(
                    hasText(localized.getString(R.string.trends_exact_values)),
                )
                compose.onNodeWithText(localized.getString(R.string.trends_exact_values)).performScrollTo().assertIsDisplayed()
                captureAndInspect("trends-$language-$mode.png")

                compose.onNodeWithText(localized.getString(R.string.navigation_settings)).performClick()
                awaitText(localized.getString(R.string.aquariums_title))
                assertAction(localized.getString(R.string.add_aquarium))
                captureAndInspect("settings-aquariums-$language-$mode.png")
                scrollSettingsTo(localized.getString(R.string.portable_data_title))
                captureAndInspect("settings-export-$language-$mode.png")
                scrollSettingsTo(localized.getString(R.string.account_actions))
                captureAndInspect("settings-account-$language-$mode.png")
                scrollSettingsTo(localized.getString(R.string.feedback_title))
                val sendFeedback = localized.getString(R.string.send_feedback)
                compose.onNode(hasText(sendFeedback) and hasClickAction()).performScrollTo()
                assertAction(sendFeedback)
                captureAndInspect("settings-feedback-$language-$mode.png")
                compose.onNode(hasText(sendFeedback) and hasClickAction()).performClick()
                awaitText(localized.getString(R.string.feedback_privacy_explanation))
                captureAndInspect("feedback-dialog-$language-$mode.png")
                compose.onNodeWithText(localized.getString(R.string.cancel)).performClick()

                compose.onNodeWithTag("settings-list").performScrollToNode(
                    hasText(localized.getString(R.string.view_free_and_pro)),
                )
                val viewPro = localized.getString(R.string.view_free_and_pro)
                assertAction(viewPro)
                compose.onNodeWithText(viewPro).performClick()
                awaitText(localized.getString(R.string.paywall_title))
                compose.onNode(
                    hasText(localized.getString(R.string.back_to_settings)) and hasClickAction(),
                ).performScrollTo()
                assertAction(localized.getString(R.string.back_to_settings))
                captureAndInspect("pro-$language-$mode.png")
            }
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertMinimumTouchTarget(bounds: Rect) {
        val density = context.resources.displayMetrics.density
        assertTrue("touch width must be >= 48dp, was ${bounds.width / density}", bounds.width / density >= 47.9f)
        assertTrue("touch height must be >= 48dp, was ${bounds.height / density}", bounds.height / density >= 47.9f)
    }

    private fun configure(language: String, darkLarge: Boolean) {
        localeManager.applicationLocales = LocaleList.forLanguageTags(language)
        uiModeManager.setApplicationNightMode(
            if (darkLarge) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
        )
        instrumentation.uiAutomation.executeShellCommand(
            "settings put system font_scale ${if (darkLarge) "1.5" else "1.0"}",
        ).close()
    }

    private fun localizedContext(language: String): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(LocaleList.forLanguageTags(language)[0]) },
    )

    private fun assertAction(label: String) {
        val action = compose.onNode(hasText(label) and hasClickAction())
        action.assertIsDisplayed()
        assertMinimumTouchTarget(action.fetchSemanticsNode().boundsInRoot)
    }

    private fun scrollSettingsTo(label: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
        assertTrue(compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "localized-captures").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { stream ->
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun captureAndInspect(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("capture must have a usable viewport", bitmap.width >= 720 && bitmap.height >= 1_000)
        val colors = buildSet {
            val xStep = (bitmap.width / 80).coerceAtLeast(1)
            val yStep = (bitmap.height / 120).coerceAtLeast(1)
            for (x in 0 until bitmap.width step xStep) {
                for (y in 0 until bitmap.height step yStep) add(bitmap.getPixel(x, y))
            }
        }
        val luminance = colors.map { color ->
            (android.graphics.Color.red(color) * 0.2126 + android.graphics.Color.green(color) * 0.7152 +
                android.graphics.Color.blue(color) * 0.0722) / 255.0
        }
        assertTrue("capture must not be blank or uniformly clipped", colors.size >= 3)
        assertTrue("capture must contain visually distinguishable foreground and background", luminance.max() - luminance.min() >= 0.08)
        val directory = File(context.getExternalFilesDir(null), "localized-captures").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { stream ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
