package com.maximerollin.aqualog.shared

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProPurchaseCoordinatorTest {
    @Test
    fun `a purchased Play transaction grants Pro only after server verification`() = runTest {
        val billing = DeterministicBillingGateway()
        val verifier = DeterministicPurchaseVerifier(
            PurchaseVerificationResult.Verified(
                ProEntitlement(productId = ANNUAL_PRO_PRODUCT_ID, validUntilEpochMillis = 1_800_000_000_000L),
            ),
        )
        val coordinator = ProPurchaseCoordinator(billing, verifier)

        assertEquals(ProPurchaseOutcome.AwaitingPlayConfirmation, coordinator.startPurchase())
        assertEquals(false, coordinator.hasProEntitlement)

        val outcome = coordinator.resolve(
            PlayPurchaseUpdate.Purchased(ANNUAL_PRO_PRODUCT_ID, "play-purchase-token"),
        )

        assertIs<ProPurchaseOutcome.Entitled>(outcome)
        assertEquals(true, coordinator.hasProEntitlement)
        assertEquals("play-purchase-token", verifier.lastPurchaseToken)
    }

    @Test
    fun `pending cancelled failed and rejected purchases never grant Pro and remain recoverable`() = runTest {
        val billing = DeterministicBillingGateway()
        val verifier = DeterministicPurchaseVerifier(PurchaseVerificationResult.Rejected("invalid receipt"))
        val coordinator = ProPurchaseCoordinator(billing, verifier)

        assertEquals(ProPurchaseOutcome.Pending, coordinator.resolve(PlayPurchaseUpdate.Pending))
        assertEquals(ProPurchaseOutcome.Cancelled, coordinator.resolve(PlayPurchaseUpdate.Cancelled))
        assertEquals(
            ProPurchaseOutcome.Failed("Play unavailable"),
            coordinator.resolve(PlayPurchaseUpdate.Failed("Play unavailable")),
        )
        assertEquals(
            ProPurchaseOutcome.Failed("invalid receipt"),
            coordinator.resolve(PlayPurchaseUpdate.Purchased(ANNUAL_PRO_PRODUCT_ID, "rejected-token")),
        )
        assertEquals(false, coordinator.hasProEntitlement)

        assertEquals(ProPurchaseOutcome.AwaitingPlayConfirmation, coordinator.startPurchase())
        assertEquals(1, billing.launchCount)
    }

    @Test
    fun `restoring an owned purchase also requires server verification`() = runTest {
        val billing = DeterministicBillingGateway(
            restored = PlayPurchaseUpdate.Purchased(ANNUAL_PRO_PRODUCT_ID, "restored-token"),
        )
        val verifier = DeterministicPurchaseVerifier(
            PurchaseVerificationResult.Verified(
                ProEntitlement(ANNUAL_PRO_PRODUCT_ID, 1_800_000_000_000L),
            ),
        )
        val coordinator = ProPurchaseCoordinator(billing, verifier)

        val outcome = coordinator.restorePurchase()

        assertIs<ProPurchaseOutcome.Entitled>(outcome)
        assertEquals("restored-token", verifier.lastPurchaseToken)
    }
}

private class DeterministicBillingGateway(
    private val restored: PlayPurchaseUpdate? = null,
) : ProBillingGateway {
    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = emptyFlow()
    var launchCount = 0

    override suspend fun loadAnnualOffer(): AnnualProOffer = AnnualProOffer(
        productId = ANNUAL_PRO_PRODUCT_ID,
        formattedPrice = "14,99 €",
        trialDays = 14,
        formattedBillingDate = "21 octobre 2026",
    )

    override suspend fun launchAnnualPurchase(): BillingLaunchResult {
        launchCount += 1
        return BillingLaunchResult.Started
    }

    override suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate? = restored
}

private class DeterministicPurchaseVerifier(
    private val result: PurchaseVerificationResult,
) : ProPurchaseVerifier {
    var lastPurchaseToken: String? = null

    override suspend fun verify(productId: String, purchaseToken: String): PurchaseVerificationResult {
        lastPurchaseToken = purchaseToken
        return result
    }
}
