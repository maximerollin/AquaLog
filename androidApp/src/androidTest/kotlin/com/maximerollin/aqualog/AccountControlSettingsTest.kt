package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.CloudAccountDeletionResult
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class AccountControlSettingsTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: TestAquaLogApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestAquaLogApplication

    private fun prepare() {
        application.resetRepository()
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                "Account Aquarium",
                80.0,
                VolumeUnit.LITERS,
                AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            application.linkAccount()
        }
    }

    @Test
    fun settings_exports_before_explicit_keep_or_remove_sign_out_choice() {
        prepare()
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(5_000) { application.proEntitlementCache.read() != null }
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("owner@example.com"))
            composeRule.onNodeWithText("owner@example.com").assertIsDisplayed()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Export data"))
            composeRule.onNodeWithText("Export data").assertIsDisplayed()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Sign out"))
            composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
            composeRule.onNodeWithText("Keep local copy").assertIsDisplayed()
            composeRule.onNodeWithText("Remove local copy").assertIsDisplayed()
            composeRule.onNodeWithText("Keep local copy").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("Local-only").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Local-only"))
            composeRule.onNodeWithText("Local-only").assertIsDisplayed()
            // Signed out, the widgets no longer know Pro.
            check(application.proEntitlementCache.read() == null)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun completed_deletion_clears_the_cached_pro() {
        prepare()
        application.grantPro(ProEntitlement(LIFETIME_PRO_PRODUCT_ID, null))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(5_000) { application.proEntitlementCache.read() != null }
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Delete account"))
            composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
            composeRule.onNodeWithText("Delete account and data").performClick()
            composeRule.waitUntil(5_000) { application.proEntitlementCache.read() == null }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun pending_deletion_keeps_export_available_and_shows_truthful_retry() {
        prepare()
        application.deletionResult = CloudAccountDeletionResult.RetryableFailure("Service temporarily unavailable")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNode(hasLabel("More")).performClick()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Delete account"))
            composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
            composeRule.onNodeWithText("Delete account and data").performClick()
            composeRule.waitUntil(5_000) {
                runCatching {
                    composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Account deletion incomplete"))
                }
                composeRule.onAllNodesWithText("Account deletion incomplete").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Service temporarily unavailable"))
            composeRule.onNodeWithText("Service temporarily unavailable").performScrollTo().assertIsDisplayed()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Export data"))
            composeRule.onNodeWithText("Export data").performScrollTo().assertIsDisplayed()
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Retry account deletion"))
            composeRule.onNodeWithText("Retry account deletion").performScrollTo().assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
