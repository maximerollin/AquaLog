package com.maximerollin.aqualog

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.MaintenanceActionInput
import com.maximerollin.aqualog.shared.MaintenanceActionType
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.ParameterDefinition
import com.maximerollin.aqualog.shared.RapidSessionInput
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.aquaLogColorScheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
    fun savedSessionPlurals_coverZeroOneAndManyInEveryLanguage() {
        listOf("en", "fr", "de", "it", "es").forEach { language ->
            val resources = localizedContext(language).resources
            val singularMeasurements = resources.getQuantityString(R.plurals.session_measurement_count, 1, 1)
            val pluralMeasurements = resources.getQuantityString(R.plurals.session_measurement_count, 2, 2)
            val singularActions = resources.getQuantityString(R.plurals.session_maintenance_count, 1, 1)
            val pluralActions = resources.getQuantityString(R.plurals.session_maintenance_count, 2, 2)
            assertFalse("$language measurement forms", singularMeasurements.removePrefix("1 ") == pluralMeasurements.removePrefix("2 "))
            assertFalse("$language maintenance forms", singularActions.removePrefix("1 ") == pluralActions.removePrefix("2 "))
            listOf(0, 1, 2).forEach { count ->
                val measurements = resources.getQuantityString(R.plurals.session_measurement_count, count, count)
                val actions = resources.getQuantityString(R.plurals.session_maintenance_count, count, count)
                assertTrue("$language measurements $count", measurements.startsWith(count.toString()))
                assertTrue("$language actions $count", actions.startsWith(count.toString()))
            }
        }
    }

    @Test
    fun activeMaterialColorRoles_meetWcagContrastInLightAndDarkThemes() {
        listOf(false, true).forEach { dark ->
            val scheme = aquaLogColorScheme(context, dark)
            val mode = if (dark) "dark" else "light"
            listOf(
                "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiaryContainer/tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onError/error" to (scheme.onError to scheme.error),
                "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "onSurface/surface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant/surfaceVariant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                "inverseOnSurface/inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                "error/surface" to (scheme.error to scheme.surface),
                "primary/surface" to (scheme.primary to scheme.surface),
            ).forEach { (name, colors) -> assertContrast("$mode $name", colors.first, colors.second, 4.5) }
            listOf(
                "outline/surface" to (scheme.outline to scheme.surface),
            ).forEach { (name, colors) -> assertContrast("$mode $name", colors.first, colors.second, 3.0) }
        }
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
                    assertCriticalText(welcome)
                    assertEssentialActions(start)
                    captureAndInspect("welcome-$language-$mode.png")

                    compose.onNodeWithText(start).performClick()
                    val profile = localized.getString(R.string.profile_title)
                    val continueLabel = localized.getString(R.string.continue_to_aquarium)
                    awaitText(continueLabel)
                    assertCriticalText(profile)
                    val continueAction = compose.onNode(hasText(continueLabel) and hasClickAction())
                    continueAction.performScrollTo().assertIsDisplayed()
                    assertEssentialActions(continueLabel)
                    captureAndInspect("profile-$language-$mode.png")
                }
            }
        }
    }

    @Test
    fun configuredCriticalScreens_haveFiveLocaleSmokeCaptures_andDarkLargeCoverage() {
        val configurations = listOf("en", "fr", "de", "it", "es").flatMap { language ->
            listOf(Triple(language, "light", false), Triple(language, "dark-large", true))
        }
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
                assertEssentialActions(
                    newSession,
                    localized.getString(R.string.open_tasks),
                    localized.getString(R.string.navigation_history),
                    localized.getString(R.string.navigation_settings),
                )
                assertCriticalText(localized.getString(R.string.home_title))
                captureAndInspect("home-$language-$mode.png")

                compose.onNodeWithText(newSession).performClick()
                val saveSession = localized.getString(R.string.save_session)
                awaitText(saveSession)
                assertAction(saveSession)
                assertCriticalText(localized.getString(R.string.measurements_heading))
                compose.onAllNodes(hasImeAction(ImeAction.Next)).onFirst().assertIsFocused().performImeAction()
                captureAndInspect("rapid-session-$language-$mode.png")
                pressBack()
                val cancel = localized.getString(R.string.cancel)
                compose.onNodeWithTag("rapid-session-list").performScrollToNode(hasText(cancel))
                awaitText(cancel)
                assertEssentialActions(cancel)
                compose.onNodeWithText(cancel).performClick()

                val tasks = localized.getString(R.string.open_tasks)
                compose.onNodeWithText(tasks).performClick()
                awaitText(localized.getString(R.string.tasks_title))
                assertCriticalText(localized.getString(R.string.tasks_title))
                compose.onNodeWithTag("task-list").performScrollToNode(
                    hasText(localized.getString(R.string.save_task)),
                )
                compose.onNode(
                    hasText(localized.getString(R.string.save_task)) and hasClickAction(),
                ).performScrollTo()
                assertAction(localized.getString(R.string.save_task))
                captureAndInspect("tasks-$language-$mode.png")
                compose.onNodeWithTag("task-list").performScrollToNode(hasText(localized.getString(R.string.back)))
                assertEssentialActionByTag("tasks-back")
                compose.onNodeWithText(localized.getString(R.string.back)).performClick()

                compose.onNodeWithText(localized.getString(R.string.navigation_history)).performClick()
                awaitText(localized.getString(R.string.timeline_title))
                assertCriticalText(localized.getString(R.string.timeline_title))
                captureAndInspect("timeline-$language-$mode.png")
                assertEssentialActions(localized.getString(R.string.trends_title))
                compose.onNodeWithText(localized.getString(R.string.trends_title)).performClick()
                compose.onNodeWithTag("trend-list").performScrollToNode(
                    hasText(localized.getString(R.string.trends_exact_values)),
                )
                compose.onNodeWithText(localized.getString(R.string.trends_exact_values)).performScrollTo().assertIsDisplayed()
                assertCriticalText(localized.getString(R.string.trends_exact_values))
                captureAndInspect("trends-$language-$mode.png")

                compose.onNodeWithText(localized.getString(R.string.navigation_settings)).performClick()
                awaitText(localized.getString(R.string.aquariums_title))
                assertAction(localized.getString(R.string.add_aquarium))
                scrollAndAssertEssentialAction(localized.getString(R.string.edit_aquarium))
                captureAndInspect("settings-aquariums-$language-$mode.png")
                scrollSettingsTo(localized.getString(R.string.portable_data_title))
                scrollAndAssertEssentialAction(localized.getString(R.string.export_data))
                scrollAndAssertEssentialAction(localized.getString(R.string.restore_from_export))
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
                assertEssentialActionByTag("submit-feedback")
                assertEssentialActions(localized.getString(R.string.cancel))
                captureAndInspect("feedback-dialog-$language-$mode.png")
                compose.onNodeWithText(localized.getString(R.string.cancel)).performClick()

                compose.onNodeWithTag("settings-list").performScrollToNode(
                    hasText(localized.getString(R.string.view_free_and_pro)),
                )
                val viewPro = localized.getString(R.string.view_free_and_pro)
                assertAction(viewPro)
                compose.onNodeWithText(viewPro).performClick()
                awaitText(localized.getString(R.string.paywall_title))
                scrollAndAssertEssentialAction(localized.getString(R.string.buy_lifetime_in_google_play))
                scrollAndAssertEssentialAction(localized.getString(R.string.start_trial_in_google_play))
                scrollAndAssertEssentialAction(localized.getString(R.string.restore_purchase))
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
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertMinimumTouchTarget(bounds)
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("$label must not be clipped horizontally", bounds.left >= viewport.left && bounds.right <= viewport.right)
        assertTrue("$label must not be clipped vertically", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }

    private fun assertEssentialActions(vararg labels: String) {
        val actions = labels.map { label ->
            val interaction = compose.onNode(hasText(label) and hasClickAction())
            interaction.assertIsDisplayed()
            val node = interaction.fetchSemanticsNode()
            val accessibleName = buildString {
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.forEach { append(it.text) }
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.forEach { append(it) }
            }
            assertTrue("$label must expose a non-empty accessible name", accessibleName.isNotBlank())
            assertMinimumTouchTarget(node.boundsInRoot)
            assertWithinViewport(label, node.boundsInRoot)
            label to node.boundsInRoot
        }
        actions.forEachIndexed { index, first ->
            actions.drop(index + 1).forEach { second ->
                assertFalse(
                    "essential actions ${first.first} and ${second.first} must not overlap",
                    first.second.left < second.second.right && second.second.left < first.second.right &&
                        first.second.top < second.second.bottom && second.second.top < first.second.bottom,
                )
            }
        }
    }

    private fun assertCriticalText(label: String) {
        val viewports = compose.onAllNodes(isRoot()).fetchSemanticsNodes().map { it.boundsInRoot }
        val visibleNodes = compose.onAllNodes(hasText(label)).fetchSemanticsNodes().filter { node ->
            viewports.any { viewport -> contains(viewport, node.boundsInRoot) }
        }
        assertTrue("critical text must have a visible semantic node: $label", visibleNodes.isNotEmpty())
        assertTrue(
            "critical text must expose its complete semantic value: $label",
            visibleNodes.any { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .joinToString("") { it.text }.contains(label)
            },
        )
    }

    private fun assertWithinViewport(label: String, bounds: Rect) {
        val viewports = compose.onAllNodes(isRoot()).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("$label must be fully contained in one Compose root", viewports.any { contains(it, bounds) })
    }

    private fun contains(viewport: Rect, bounds: Rect): Boolean =
        bounds.left >= viewport.left && bounds.right <= viewport.right &&
            bounds.top >= viewport.top && bounds.bottom <= viewport.bottom

    private fun scrollSettingsTo(label: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
        assertTrue(compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun scrollAndAssertEssentialAction(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo()
        assertEssentialActions(label)
    }

    private fun assertEssentialActionByTag(tag: String) {
        val interaction = compose.onNodeWithTag(tag)
        interaction.assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        val accessibleName = buildString {
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.forEach { append(it.text) }
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.forEach { append(it) }
        }
        assertTrue("$tag must expose a non-empty accessible name", accessibleName.isNotBlank())
        assertMinimumTouchTarget(node.boundsInRoot)
        assertWithinViewport(tag, node.boundsInRoot)
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
        assertTrue("capture must not be blank or uniformly clipped", colors.size >= 3)
        TestStorage().openOutputFile("localized-accessibility/$name").use { stream ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, threshold: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$name contrast $ratio must be >= $threshold", ratio >= threshold)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(channel: Float): Double = channel.toDouble().let {
                if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }
}
