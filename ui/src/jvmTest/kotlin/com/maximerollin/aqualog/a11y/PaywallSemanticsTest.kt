package com.maximerollin.aqualog.a11y

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.maximerollin.aqualog.ProPurchaseStatus
import com.maximerollin.aqualog.ProUiState
import com.maximerollin.aqualog.onboarding.PaywallActions
import com.maximerollin.aqualog.onboarding.PaywallContent
import com.maximerollin.aqualog.resources.Res
import com.maximerollin.aqualog.resources.back_to_settings
import com.maximerollin.aqualog.resources.continue_for_free
import com.maximerollin.aqualog.resources.continue_with_pro
import com.maximerollin.aqualog.resources.google_play_offer_unavailable
import com.maximerollin.aqualog.resources.loading_google_play_offer
import com.maximerollin.aqualog.resources.paywall_recommended
import com.maximerollin.aqualog.resources.pro_lifetime_offer_summary
import com.maximerollin.aqualog.resources.pro_offer_summary
import com.maximerollin.aqualog.resources.try_again
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.LifetimeProOffer
import com.maximerollin.aqualog.shared.VolumeUnit
import com.maximerollin.aqualog.ui.theme.AquaLogTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/** The Offer: plan cards, « Choisir Pro », terms and the continue action (lot 5, Task 6). */
@OptIn(ExperimentalTestApi::class)
class PaywallSemanticsTest {
    private val aquarium = Aquarium("onboarding-preview", "Salon", 120.0, VolumeUnit.LITERS, 0L)
    private val both = ProUiState(
        offer = AnnualProOffer("annual", "14,99 €", 14, 0L),
        lifetimeOffer = LifetimeProOffer("lifetime", "34,99 €"),
    )
    private val calls = mutableListOf<String>()
    private val actions = PaywallActions(
        onStartAnnual = { calls += "startAnnual" },
        onStartLifetime = { calls += "startLifetime" },
        onRestore = { calls += "restore" },
        onRetryOffer = { calls += "retry" },
        onContinue = { calls += "continue" },
    )

    private fun french(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try { block() } finally { Locale.setDefault(previous) }
    }

    private fun ComposeUiTest.paywall(pro: ProUiState, onboarding: Boolean = true, fontScale: Float = 1f, height: Int = 800) {
        setContent {
            AquaLogTheme(false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.requiredSize(360.dp, height.dp)) {
                        PaywallContent(pro, isSaving = false, onboarding = onboarding, aquarium = aquarium, actions = actions)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun text(block: suspend () -> String): String = runBlocking { block() }

    @Test
    fun `annual card is selected and recommended`() = french {
        runComposeUiTest {
            paywall(both)
            onNodeWithTag("plan-annual").assertIsSelected()
            onNodeWithTag("plan-annual").assertTextContains(text { getString(Res.string.paywall_recommended) }, substring = true, ignoreCase = true)
            onNodeWithTag("plan-lifetime").assertIsNotSelected()
        }
    }

    @Test
    fun `choose pro starts the selected plan`() = french {
        runComposeUiTest {
            paywall(both)
            onNodeWithTag("choose-pro").performClick()
            assertEquals(listOf("startAnnual"), calls)
            onNodeWithTag("plan-lifetime").performClick()
            onNodeWithTag("choose-pro").performClick()
            assertEquals(listOf("startAnnual", "startLifetime"), calls)
        }
    }

    @Test
    fun `terms follow the selected card`() = french {
        runComposeUiTest {
            paywall(both)
            val annualTerms = hasText(
                text { getString(Res.string.pro_offer_summary, "@P", 14, "@D") }.substringAfter("@P").substringBefore("@D"),
                substring = true,
            )
            val lifetimeTerms = text { getString(Res.string.pro_lifetime_offer_summary, "34,99 €") }
            onNode(annualTerms).assertIsDisplayed()
            onNodeWithTag("plan-lifetime").performClick()
            onNodeWithText(lifetimeTerms).assertIsDisplayed()
        }
    }

    @Test
    fun `busy purchase disables choose pro`() = french {
        runComposeUiTest {
            paywall(both.copy(purchaseStatus = ProPurchaseStatus.PENDING))
            onNodeWithTag("choose-pro").assertIsNotEnabled()
        }
    }

    @Test
    fun `no offer shows the error and retry, no cards`() = french {
        runComposeUiTest {
            paywall(ProUiState())
            onNodeWithText(text { getString(Res.string.google_play_offer_unavailable) }).assertIsDisplayed()
            onNodeWithText(text { getString(Res.string.try_again) }).assertIsDisplayed()
            onNodeWithTag("plan-annual").assertDoesNotExist()
            onNodeWithTag("choose-pro").assertDoesNotExist()
            onNodeWithText(text { getString(Res.string.continue_for_free) }).assertExists()
        }
    }

    @Test
    fun `loading shows the spinner text`() = french {
        runComposeUiTest {
            paywall(ProUiState(isLoadingOffer = true))
            onNodeWithText(text { getString(Res.string.loading_google_play_offer) }).assertIsDisplayed()
            onNodeWithTag("plan-annual").assertDoesNotExist()
        }
    }

    @Test
    fun `from More the continue reads back to settings`() = french {
        runComposeUiTest {
            paywall(both, onboarding = false)
            onNodeWithText(text { getString(Res.string.back_to_settings) }).assertExists()
            onNodeWithTag("onboarding-progress").assertDoesNotExist()
        }
    }

    @Test
    fun `active onboarding continues with pro`() = french {
        runComposeUiTest {
            paywall(both.copy(purchaseStatus = ProPurchaseStatus.ACTIVE))
            onNodeWithText(text { getString(Res.string.continue_with_pro) }).assertExists()
        }
    }

    @Test
    fun `German 150 percent stacks the cards`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        try {
            runComposeUiTest {
                paywall(both, fontScale = 1.5f, height = 1800)
                val annual = onNodeWithTag("plan-annual").fetchSemanticsNode().boundsInRoot
                val lifetime = onNodeWithTag("plan-lifetime").fetchSemanticsNode().boundsInRoot
                assertTrue(lifetime.top >= annual.bottom, "cards stacked: $annual / $lifetime")
            }
            runComposeUiTest {
                paywall(both, fontScale = 1.5f, height = 700)
                onNodeWithTag("choose-pro").assertIsDisplayed()
            }
        } finally { Locale.setDefault(previous) }
    }
}
