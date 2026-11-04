package com.maximerollin.aqualog

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.VolumeUnit
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AquariumPhotoFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application: TestAquaLogApplication
        get() = instrumentation.targetContext.applicationContext as TestAquaLogApplication

    @Test
    fun vignette_opensTheViewer_withChangeAndRemove() {
        configure("fr", darkLarge = false)
        application.resetRepository()
        runBlocking {
            val repository = application.aquariumRepository
            val salon = repository.createConfiguredAquarium(
                "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            ).aquarium
            repository.setAquariumPhoto(salon.id, jpeg())
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                waitFor(hasContentDescription("Voir la photo de Salon"))
                val vignette = composeRule.onNodeWithContentDescription("Voir la photo de Salon")
                assertMinimumTouchTarget(vignette.fetchSemanticsNode().touchBoundsInRoot)
                capture("photo-home-fr.png")

                vignette.performClick()

                waitFor(hasText("Changer la photo"))
                composeRule.onNodeWithText("Retirer la photo").assertExists()
                waitFor(hasContentDescription("Photo de Salon"))
                capture("photo-viewer-fr.png")
            }
        } finally {
            restoreDisplay()
        }
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 103, 94)) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        TestStorage().openOutputFile("photo/$name").use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
