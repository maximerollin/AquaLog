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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
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

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertMinimumTouchTarget(bounds: Rect) {
        val density = context.resources.displayMetrics.density
        assertTrue("touch width must be >= 48dp, was ${bounds.width / density}", bounds.width / density >= 47.9f)
        assertTrue("touch height must be >= 48dp, was ${bounds.height / density}", bounds.height / density >= 47.9f)
    }

    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "localized-captures").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { stream ->
            assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
