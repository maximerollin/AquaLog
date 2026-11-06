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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
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

    @Test
    fun vignette_followsAReplacedThenRemovedPhoto() {
        configure("fr", darkLarge = false)
        application.resetRepository()
        val repository = application.aquariumRepository
        val salon = runBlocking {
            repository.createConfiguredAquarium(
                "Salon", 120.0, VolumeUnit.LITERS, AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            ).aquarium.also { repository.setAquariumPhoto(it.id, jpeg(Color.RED)) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                waitFor(hasContentDescription("Voir la photo de Salon"))
                waitForCentre("Voir la photo de Salon") { it == Color.RED }

                runBlocking { repository.setAquariumPhoto(salon.id, jpeg(Color.BLUE)) }
                waitForCentre("Voir la photo de Salon") { it == Color.BLUE }

                runBlocking { repository.setAquariumPhoto(salon.id, null) }
                waitFor(hasContentDescription("Ajouter une photo de Salon"))
                // The mini-scene again, not the last photo.
                waitForCentre("Ajouter une photo de Salon") { it == null }
            }
        } finally {
            restoreDisplay()
        }
    }

    /** Waits until the centre of the vignette labelled [label] is pure red, pure blue, or (null) neither. */
    private fun waitForCentre(label: String, expected: (Int?) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            // captureToImage gives up after 2 s on a slow emulator; retry until the 10 s budget instead.
            val image = try {
                composeRule.onNodeWithContentDescription(label).captureToImage().asAndroidBitmap()
            } catch (_: ComposeTimeoutException) {
                return@waitUntil false
            }
            val pixel = image.getPixel(image.width / 2, image.height / 2)
            val (r, g, b) = Triple(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            expected(
                when {
                    r > 200 && g < 60 && b < 60 -> Color.RED
                    b > 200 && r < 60 && g < 60 -> Color.BLUE
                    else -> null
                },
            )
        }
    }

    private fun jpeg(color: Int = Color.rgb(20, 103, 94)): ByteArray {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
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
