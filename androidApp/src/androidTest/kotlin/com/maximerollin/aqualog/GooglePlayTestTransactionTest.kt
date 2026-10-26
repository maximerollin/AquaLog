package com.maximerollin.aqualog

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.ProPurchaseOutcome
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.AuthenticationResult
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in integration check for a real, already-confirmed Google Play license-tester transaction.
 * It never fabricates a purchase token and is skipped in ordinary connected test runs.
 */
@RunWith(AndroidJUnit4::class)
class GooglePlayTestTransactionTest {
    @Test
    fun restored_license_tester_transaction_is_verified_by_the_server() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("runGooglePlayTestTransaction") == "true")

        val testApplication = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as TestAquaLogApplication
        testApplication.resetRepository()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var gateway: RevenueCatBillingGateway
        scenario.onActivity { activity ->
            gateway = RevenueCatBillingGateway(activity.applicationContext).also { it.attach(activity) }
        }
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val tokenStorage = AndroidSecureTokenStorage(application)
        val authGateway = SupabaseAuthGateway(
            application,
            SupabaseConfiguration.fromBuildConfig(),
            tokenStorage,
        )
        val coordinator = ProPurchaseCoordinator(
            billingGateway = gateway,
            purchaseVerifier = SupabaseProPurchaseVerifier(
                SupabaseConfiguration.fromBuildConfig(),
                object : SyncSessionProvider {
                    override suspend fun currentSession(): SyncSession? = tokenStorage.read()?.let {
                        SyncSession(accountId = "license-tester", accessToken = it.accessToken)
                    }

                    override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
                        val refreshToken = tokenStorage.read()?.refreshToken ?: return null
                        val refreshed = authGateway.refreshSession(refreshToken)
                            as? AuthenticationResult.Authenticated ?: return null
                        tokenStorage.save(
                            AuthTokens(refreshed.account.accessToken, refreshed.account.refreshToken),
                        )
                        return SyncSession(refreshed.account.accountId, refreshed.account.accessToken)
                    }

                    override suspend fun requireReauthentication() = tokenStorage.clear()
                },
            ),
        )

        val offer = coordinator.loadOffer()
        val result = coordinator.restorePurchase()

        assertTrue("Expected the configured annual product", offer.productId == "aqualog_pro_annual")
        assertTrue("The real RevenueCat entitlement must be verified by the server", result is ProPurchaseOutcome.Entitled)
        scenario.close()
    }
}
