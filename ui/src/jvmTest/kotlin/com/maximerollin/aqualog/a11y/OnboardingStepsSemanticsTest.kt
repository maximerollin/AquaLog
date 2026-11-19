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
import com.maximerollin.aqualog.onboarding.OnboardingActions
import com.maximerollin.aqualog.onboarding.ProfileContent
import com.maximerollin.aqualog.onboarding.WelcomeContent
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.get_started
import com.maximerollin.aqualog.resources.onboarding_step_of
import com.maximerollin.aqualog.resources.profile_cycling
import com.maximerollin.aqualog.resources.restore_from_export
import com.maximerollin.aqualog.resources.welcome_title
import com.maximerollin.aqualog.shared.AquariumProfile
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
}
