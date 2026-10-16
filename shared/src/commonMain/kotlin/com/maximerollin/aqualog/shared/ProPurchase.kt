package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.Flow

const val ANNUAL_PRO_PRODUCT_ID = "aqualog_pro_annual"
const val LIFETIME_PRO_PRODUCT_ID = "aqualog_pro_lifetime"

data class AnnualProOffer(
    val productId: String,
    val formattedPrice: String,
    val trialDays: Int,
    val formattedBillingDate: String,
)

data class LifetimeProOffer(
    val productId: String,
    val formattedPrice: String,
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
    suspend fun launchAnnualPurchase(obfuscatedAccountId: String): BillingLaunchResult
    suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate?
    suspend fun loadLifetimeOffer(): LifetimeProOffer
    suspend fun launchLifetimePurchase(obfuscatedAccountId: String): BillingLaunchResult
    suspend fun restoreLifetimePurchase(): PlayPurchaseUpdate?
}

sealed interface PurchaseAccountIdentityResult {
    data class Available(val obfuscatedAccountId: String) : PurchaseAccountIdentityResult
    data object AuthenticationRequired : PurchaseAccountIdentityResult
    data class Failed(val reason: String) : PurchaseAccountIdentityResult
}

data class ProEntitlement(
    val productId: String,
    /** Null means a non-expiring, server-verified lifetime entitlement. */
    val validUntilEpochMillis: Long?,
)

sealed interface PurchaseVerificationResult {
    data class Verified(val entitlement: ProEntitlement) : PurchaseVerificationResult
    data class Rejected(val reason: String) : PurchaseVerificationResult
    data object AuthenticationRequired : PurchaseVerificationResult
    data class Failed(val reason: String) : PurchaseVerificationResult
}

interface ProPurchaseVerifier {
    suspend fun loadPurchaseAccountIdentity(): PurchaseAccountIdentityResult
    suspend fun loadEntitlement(): PurchaseVerificationResult
    suspend fun verify(productId: String, purchaseToken: String): PurchaseVerificationResult
}

sealed interface ProPurchaseOutcome {
    data object AwaitingPlayConfirmation : ProPurchaseOutcome
    data object Pending : ProPurchaseOutcome
    data object Cancelled : ProPurchaseOutcome
    data object NotEntitled : ProPurchaseOutcome
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

    suspend fun loadLifetimeOffer(): LifetimeProOffer = billingGateway.loadLifetimeOffer()

    suspend fun refreshEntitlement(): ProPurchaseOutcome = applyVerification(
        purchaseVerifier.loadEntitlement(),
        rejectionIsFailure = false,
    )

    suspend fun startPurchase(): ProPurchaseOutcome = when (val identity = purchaseVerifier.loadPurchaseAccountIdentity()) {
        is PurchaseAccountIdentityResult.Available -> {
            when (val launch = billingGateway.launchAnnualPurchase(identity.obfuscatedAccountId)) {
                BillingLaunchResult.Started -> ProPurchaseOutcome.AwaitingPlayConfirmation
                is BillingLaunchResult.Failed -> ProPurchaseOutcome.Failed(launch.reason)
            }
        }
        PurchaseAccountIdentityResult.AuthenticationRequired -> ProPurchaseOutcome.AuthenticationRequired
        is PurchaseAccountIdentityResult.Failed -> ProPurchaseOutcome.Failed(identity.reason)
    }

    suspend fun startLifetimePurchase(): ProPurchaseOutcome = when (
        val identity = purchaseVerifier.loadPurchaseAccountIdentity()
    ) {
        is PurchaseAccountIdentityResult.Available -> {
            when (val launch = billingGateway.launchLifetimePurchase(identity.obfuscatedAccountId)) {
                BillingLaunchResult.Started -> ProPurchaseOutcome.AwaitingPlayConfirmation
                is BillingLaunchResult.Failed -> ProPurchaseOutcome.Failed(launch.reason)
            }
        }
        PurchaseAccountIdentityResult.AuthenticationRequired -> ProPurchaseOutcome.AuthenticationRequired
        is PurchaseAccountIdentityResult.Failed -> ProPurchaseOutcome.Failed(identity.reason)
    }

    suspend fun restorePurchase(): ProPurchaseOutcome {
        val restored = billingGateway.restoreLifetimePurchase()
            ?: billingGateway.restoreAnnualPurchase()
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
        if (update.productId !in setOf(ANNUAL_PRO_PRODUCT_ID, LIFETIME_PRO_PRODUCT_ID)) {
            return ProPurchaseOutcome.Failed("Unexpected Google Play product")
        }
        return applyVerification(
            purchaseVerifier.verify(update.productId, update.purchaseToken),
            rejectionIsFailure = true,
        )
    }

    private fun applyVerification(
        result: PurchaseVerificationResult,
        rejectionIsFailure: Boolean,
    ): ProPurchaseOutcome = when (result) {
            is PurchaseVerificationResult.Verified -> {
                entitlement = result.entitlement
                ProPurchaseOutcome.Entitled(result.entitlement)
            }
            is PurchaseVerificationResult.Rejected -> {
                entitlement = null
                if (rejectionIsFailure) {
                    ProPurchaseOutcome.Failed(result.reason)
                } else {
                    ProPurchaseOutcome.NotEntitled
                }
            }
            PurchaseVerificationResult.AuthenticationRequired -> {
                entitlement = null
                ProPurchaseOutcome.AuthenticationRequired
            }
            is PurchaseVerificationResult.Failed -> {
                entitlement = null
                ProPurchaseOutcome.Failed(result.reason)
            }
        }
}
