package com.maximerollin.aqualog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.AquariumProfile
import com.maximerollin.aqualog.shared.OnboardingPresets
import com.maximerollin.aqualog.shared.SyncFailure
import com.maximerollin.aqualog.shared.SyncFailureCategory
import com.maximerollin.aqualog.shared.SyncLocalDataSource
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStatusFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun localAquariumCreation_finishesImmediatelyAndShowsPendingSync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as TestAquaLogApplication).resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithText("Continue to Aquarium").performClick()
        composeRule.onNodeWithText("Aquarium name").performTextInput("Amazonien")
        composeRule.onNodeWithText("Volume").performTextInput("120")
        composeRule.onNodeWithText("Continue to Parameters").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Try a mini Session"))
        composeRule.onNodeWithText("Try a mini Session").performClick()
        composeRule.onNodeWithText("See Free and Pro").performClick()
        composeRule.onNodeWithText("Continue for free").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("○ Sync pending")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("○ Sync pending").assertIsDisplayed()
        composeRule.onNodeWithText("New Session").assertIsDisplayed()
        scenario.close()
    }

    @Test
    fun failedSync_keepsLocalAquariumVisible_explainsFailure_andAllowsManualRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TestAquaLogApplication
        application.resetRepository()
        val syncLocalData = application.aquariumRepository as SyncLocalDataSource
        runBlocking {
            application.aquariumRepository.createConfiguredAquarium(
                "Offline Aquarium",
                120.0,
                VolumeUnit.LITERS,
                AquariumProfile.ESTABLISHED,
                OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )
            val operationIds = syncLocalData.pendingSyncBatch().operations.map { it.id }
            syncLocalData.recordSyncFailure(
                operationIds,
                SyncFailure(SyncFailureCategory.NETWORK, "Connection timed out"),
                nextRetryAtEpochMillis = Long.MAX_VALUE,
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Offline Aquarium").assertIsDisplayed()
        composeRule.onNodeWithText("Sync unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Network connection failed. Your local data remains available.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Connection timed out").assertIsDisplayed()
        composeRule.onNodeWithText("Retry sync").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("○ Sync pending")).fetchSemanticsNodes().isNotEmpty()
        }

        scenario.close()
    }
}
