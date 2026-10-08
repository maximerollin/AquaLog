package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.Flow

const val ANNUAL_PRO_PRODUCT_ID = "aqualog_pro_annual"

data class AnnualProOffer(
    val productId: String,
    val formattedPrice: String,
    val trialDays: Int,
    val formattedBillingDate: String,
)

sealed interface BillingLaunchResult {
    data object Started : BillingLaunchResult
    data class Failed(val reason: String) : BillingLaunchResult
}

sealed interface PlayPurchaseUpdate {
    data object Pending : PlayPurchaseUpdate
    data object Cancelled : PlayPurchaseUpdate
    data class Failed(val reason: String) : PlayPurchaseUpdate
    data class Purchased(val productId: String, val purchaseToken: String) : PlayPurchaseUpdate
}

interface ProBillingGateway {
    val purchaseUpdates: Flow<PlayPurchaseUpdate>
    suspend fun loadAnnualOffer(): AnnualProOffer
    suspend fun launchAnnualPurchase(): BillingLaunchResult
    suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate?
}

data class ProEntitlement(
    val productId: String,
    val validUntilEpochMillis: Long,
)

sealed interface PurchaseVerificationResult {
    data class Verified(val entitlement: ProEntitlement) : PurchaseVerificationResult
    data class Rejected(val reason: String) : PurchaseVerificationResult
    data object AuthenticationRequired : PurchaseVerificationResult
    data class Failed(val reason: String) : PurchaseVerificationResult
}

interface ProPurchaseVerifier {
    suspend fun verify(productId: String, purchaseToken: String): PurchaseVerificationResult
}

sealed interface ProPurchaseOutcome {
    data object AwaitingPlayConfirmation : ProPurchaseOutcome
    data object Pending : ProPurchaseOutcome
    data object Cancelled : ProPurchaseOutcome
    data object AuthenticationRequired : ProPurchaseOutcome
    data class Entitled(val entitlement: ProEntitlement) : ProPurchaseOutcome
    data class Failed(val reason: String) : ProPurchaseOutcome
}

class ProPurchaseCoordinator(
    private val billingGateway: ProBillingGateway,
    private val purchaseVerifier: ProPurchaseVerifier,
) {
    val purchaseUpdates: Flow<PlayPurchaseUpdate> get() = billingGateway.purchaseUpdates

    var entitlement: ProEntitlement? = null
        private set

    val hasProEntitlement: Boolean get() = entitlement != null

    suspend fun loadOffer(): AnnualProOffer = billingGateway.loadAnnualOffer()

    suspend fun startPurchase(): ProPurchaseOutcome = when (val launch = billingGateway.launchAnnualPurchase()) {
        BillingLaunchResult.Started -> ProPurchaseOutcome.AwaitingPlayConfirmation
        is BillingLaunchResult.Failed -> ProPurchaseOutcome.Failed(launch.reason)
    }

    suspend fun restorePurchase(): ProPurchaseOutcome {
        val restored = billingGateway.restoreAnnualPurchase()
            ?: return ProPurchaseOutcome.Failed("No active Google Play purchase was found")
        return resolve(restored)
    }

    suspend fun resolve(update: PlayPurchaseUpdate): ProPurchaseOutcome = when (update) {
        PlayPurchaseUpdate.Pending -> ProPurchaseOutcome.Pending
        PlayPurchaseUpdate.Cancelled -> ProPurchaseOutcome.Cancelled
        is PlayPurchaseUpdate.Failed -> ProPurchaseOutcome.Failed(update.reason)
        is PlayPurchaseUpdate.Purchased -> verify(update)
    }

    private suspend fun verify(update: PlayPurchaseUpdate.Purchased): ProPurchaseOutcome {
        if (update.productId != ANNUAL_PRO_PRODUCT_ID) {
            return ProPurchaseOutcome.Failed("Unexpected Google Play product")
        }
        return when (val result = purchaseVerifier.verify(update.productId, update.purchaseToken)) {
            is PurchaseVerificationResult.Verified -> {
                entitlement = result.entitlement
                ProPurchaseOutcome.Entitled(result.entitlement)
            }
            is PurchaseVerificationResult.Rejected -> ProPurchaseOutcome.Failed(result.reason)
            PurchaseVerificationResult.AuthenticationRequired -> ProPurchaseOutcome.AuthenticationRequired
            is PurchaseVerificationResult.Failed -> ProPurchaseOutcome.Failed(result.reason)
        }
    }
}
