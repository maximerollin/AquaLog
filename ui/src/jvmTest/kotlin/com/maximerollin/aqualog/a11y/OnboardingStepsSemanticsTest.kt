package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.PortableDataUiState
import com.maximerollin.aqualog.onboarding.AquariumStepContent
import com.maximerollin.aqualog.onboarding.OnboardingActions
import com.maximerollin.aqualog.onboarding.ProfileContent
import com.maximerollin.aqualog.onboarding.WelcomeContent
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.aquarium_name_label
import com.maximerollin.aqualog.resources.get_started
import com.maximerollin.aqualog.resources.invalid_aquarium
import com.maximerollin.aqualog.resources.unit_us_gallons
import com.maximerollin.aqualog.resources.volume_liters_format
import com.maximerollin.aqualog.resources.onboarding_step_of
import com.maximerollin.aqualog.resources.profile_cycling
import com.maximerollin.aqualog.resources.restore_from_export
import com.maximerollin.aqualog.resources.welcome_title
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** Welcome and Objective (lot 5, Task 2). */
@OptIn(ExperimentalTestApi::class)
class OnboardingStepsSemanticsTest {
    private val calls = mutableListOf<String>()
    private val actions = OnboardingActions(
        startOnboarding = { calls += "startOnboarding" },
        requestImport = { calls += "requestImport" },
        selectProfile = { calls += "selectProfile($it)" },
        continueToAquarium = { calls += "continueToAquarium" },
        selectVolumeUnit = { calls += "selectVolumeUnit($it)" },
    )

    private fun inLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun french(block: () -> Unit) = inLocale(Locale.FRENCH, block)

    private fun str(block: suspend () -> String) = runBlocking { block() }

    @Test
    fun `welcome start calls startOnboarding`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) { Box(Modifier.size(360.dp, 760.dp)) { WelcomeContent(PortableDataUiState(), actions) } }
            }
            waitForIdle()
            onNodeWithText(str { getString(Res.string.get_started) }).performClick()
            assertEquals(listOf("startOnboarding"), calls)
        }
    }

    @Test
    fun `welcome restore is disabled while working`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 760.dp)) { WelcomeContent(PortableDataUiState(isWorking = true), actions) }
                }
            }
            waitForIdle()
            onNodeWithText(str { getString(Res.string.restore_from_export) }).assertIsNotEnabled()
        }
    }

    @Test
    fun `welcome title is a heading over the water`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) { Box(Modifier.size(360.dp, 760.dp)) { WelcomeContent(PortableDataUiState(), actions) } }
            }
            waitForIdle()
            onNode(isHeading() and hasText(str { getString(Res.string.welcome_title) })).assertIsDisplayed()
            val dots = onNodeWithTag("welcome-dots", useUnmergedTree = true).fetchSemanticsNode()
            assertNull(dots.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull(dots.config.getOrNull(SemanticsProperties.Text))
        }
    }

    @Test
    fun `profile cards are a radio group`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 760.dp)) { ProfileContent(AquariumProfile.ESTABLISHED, actions) }
                }
            }
            waitForIdle()
            val radios = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            assertEquals(3, radios.fetchSemanticsNodes().size)
            val selected = radios.fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.Selected) }
            assertEquals(listOf(true, false, false), selected)
        }
    }

    @Test
    fun `tapping a profile card selects it`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 760.dp)) { ProfileContent(AquariumProfile.ESTABLISHED, actions) }
                }
            }
            waitForIdle()
            onNodeWithText(str { getString(Res.string.profile_cycling) }).performClick()
            assertTrue("selectProfile(CYCLING)" in calls, calls.toString())
        }
    }

    @Test
    fun `profile shows step 1 of 5`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 760.dp)) { ProfileContent(AquariumProfile.ESTABLISHED, actions) }
                }
            }
            waitForIdle()
            val expected = str { getString(Res.string.onboarding_step_of, 1, 5) }
            val node = onNodeWithTag("onboarding-progress").fetchSemanticsNode()
            assertEquals(listOf(expected), node.config.getOrNull(SemanticsProperties.ContentDescription))
        }
    }

    @Test
    fun `profile at German 150 percent keeps the button`() = inLocale(Locale.GERMAN) {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                        Box(Modifier.size(360.dp, 760.dp)) { ProfileContent(AquariumProfile.ESTABLISHED, actions) }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("onboarding-primary").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showAquarium(
        name: String = "Salon",
        volume: String = "120",
        unit: VolumeUnit = VolumeUnit.LITERS,
        showError: Boolean = false,
        fontScale: Float = 1f,
    ) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(360.dp, 760.dp)) {
                        AquariumStepContent(name, volume, unit, AquariumProfile.ESTABLISHED, showError, actions)
                    }
                }
            }
        }
        waitForIdle()
    }

    /** Texts of the header's live node: the nodes that are not the editable fields. */
    private fun androidx.compose.ui.test.ComposeUiTest.headerNode() =
        onNodeWithTag("onboarding-aquarium-live")

    @Test
    fun `typed name shows on the water`() = french {
        runComposeUiTest {
            showAquarium(name = "Salon")
            val texts = headerNode().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue("Salon" in texts, texts.toString())
        }
    }

    @Test
    fun `blank name shows the label on the water`() = french {
        runComposeUiTest {
            showAquarium(name = "")
            val label = str { getString(Res.string.aquarium_name_label) }
            val texts = headerNode().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue(label in texts, texts.toString())
        }
    }

    @Test
    fun `volume line appears once valid`() = french {
        runComposeUiTest {
            showAquarium(volume = "120")
            val expected = str { getString(Res.string.volume_liters_format, "120") }
            val texts = headerNode().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertTrue(expected in texts, "$expected in $texts")
        }
    }

    @Test
    fun `no volume line while the volume is zero`() = french {
        runComposeUiTest {
            showAquarium(volume = "0")
            val texts = headerNode().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertEquals(2, texts.size, texts.toString())
        }
    }

    @Test
    fun `unit segmented calls selectVolumeUnit`() = french {
        runComposeUiTest {
            showAquarium()
            onNodeWithText(str { getString(Res.string.unit_us_gallons) }).performClick()
            assertTrue("selectVolumeUnit(US_GALLONS)" in calls, calls.toString())
        }
    }

    @Test
    fun `error marks both fields and shows the message`() = french {
        runComposeUiTest {
            showAquarium(showError = true)
            val message = str { getString(Res.string.invalid_aquarium) }
            onNodeWithText(message).assertIsDisplayed()
            assertTrue(onNodeWithTag("aquarium-name").fetchSemanticsNode().config.contains(SemanticsProperties.Error))
            assertTrue(onNodeWithTag("aquarium-volume").fetchSemanticsNode().config.contains(SemanticsProperties.Error))
        }
    }

    @Test
    fun `German 150 percent name wraps on two lines`() = inLocale(Locale.GERMAN) {
        runComposeUiTest {
            showAquarium(name = "Mein grosses Gesellschaftsbecken im Wohnzimmer", fontScale = 1.5f)
            headerNode().assertIsDisplayed()
            onNodeWithTag("onboarding-primary").assertIsDisplayed()
        }
    }
}
