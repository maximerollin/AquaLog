package com.maximerollin.aqualog.a11y

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.onboarding.OnboardingHeader
import com.maximerollin.aqualog.onboarding.OnboardingProgress
import com.maximerollin.aqualog.onboarding.OnboardingScaffold
import com.maximerollin.aqualog.onboarding.OnboardingTitle
import com.maximerollin.aqualog.onboarding.WelcomeDots
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.onboarding_step_of
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** The shared onboarding pieces: progress, welcome dots, scaffold and header (lot 5, Task 1). */
@OptIn(ExperimentalTestApi::class)
class OnboardingFrameSemanticsTest {
    private val aquarium = Aquarium("onboarding-preview", "Salon", 120.0, VolumeUnit.LITERS, 0L)

    private fun french(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `progress is one node saying the step`() = french {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { Box(Modifier.size(360.dp, 200.dp)) { OnboardingProgress(2) } } }
            waitForIdle()
            val expected = runBlocking { getString(Res.string.onboarding_step_of, 2, 5) }
            val node = onNodeWithTag("onboarding-progress").fetchSemanticsNode()
            assertEquals(listOf(expected), node.config.getOrNull(SemanticsProperties.ContentDescription))
            assertEquals(ProgressBarRangeInfo(2f / 5, 0f..1f), node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
            assertTrue(node.children.isEmpty())
        }
    }

    @Test
    fun `welcome dots are silent`() = french {
        runComposeUiTest {
            setContent { AquaLogTheme(false) { Box(Modifier.size(360.dp, 200.dp)) { WelcomeDots() } } }
            waitForIdle()
            val node = onNodeWithTag("welcome-dots", useUnmergedTree = true).fetchSemanticsNode()
            assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
            assertNull(node.config.getOrNull(SemanticsProperties.Text))
        }
    }

    @Test
    fun `scaffold primary button is a 56 dp button`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 700.dp)) {
                        OnboardingScaffold(rememberLazyListState(), "Continuer", onPrimary = {}) {}
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("onboarding-primary").assertHasClickAction().assertHeightIsAtLeast(56.dp)
        }
    }

    @Test
    fun `header title is a heading`() = french {
        runComposeUiTest {
            setContent {
                AquaLogTheme(false) {
                    Box(Modifier.size(360.dp, 700.dp)) {
                        OnboardingHeader(aquarium, 200.dp, animate = false, step = 2) {
                            OnboardingTitle("Que suivez-vous ?", null)
                        }
                    }
                }
            }
            waitForIdle()
            onNode(isHeading() and hasText("Que suivez-vous ?")).assertExists()
        }
    }
}
