package com.maximerollin.aqualog

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PortableDataFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun initializeIntents() {
        Intents.init()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    @Test
    fun export_usesAndroidDocumentCreation_andWritesVersionedJson() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking { application.createPortableAquarium("Exported Aquarium") }
        val output = File(context.cacheDir, "portable-export.json").apply { delete() }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(
            ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(output))),
        )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNodeWithText("Settings").performClick()
            composeRule.onNodeWithText("Export data").performClick()

            intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType("application/json")))
            composeRule.waitUntil(timeoutMillis = 10_000) {
                output.exists() && output.readText().contains("\"formatVersion\": 1")
            }
            composeRule.onNodeWithText("Export ready").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun import_isAvailableBeforeOnboarding_andRestoresThroughAndroidDocumentSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        runBlocking { application.createPortableAquarium("Restored Aquarium") }
        val payload = runBlocking { application.portableDataService.export() }
        application.resetRepository()
        val input = File(context.cacheDir, "portable-import.json").apply { writeText(payload) }
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
            ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(input))),
        )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNodeWithText("Restore from export").performClick()

            intended(
                allOf(
                    hasAction(Intent.ACTION_OPEN_DOCUMENT),
                    hasType("*/*"),
                    hasExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json")),
                ),
            )
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("Restored Aquarium").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Restored Aquarium").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    private suspend fun TestAquaLogApplication.createPortableAquarium(name: String) {
        aquariumRepository.createConfiguredAquarium(
            name = name,
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
    }
}
