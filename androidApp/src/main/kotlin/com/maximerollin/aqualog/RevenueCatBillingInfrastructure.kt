package com.maximerollin.aqualog

import android.app.Activity
import android.content.Context
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.BillingLaunchResult
import com.maximerollin.aqualog.shared.LifetimeProOffer
import com.maximerollin.aqualog.shared.PlayPurchaseUpdate
import com.maximerollin.aqualog.shared.ProBillingGateway
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getProductsWith
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.SubscriptionOption
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import java.lang.ref.WeakReference
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Store operations only. CustomerInfo never grants access: Supabase reconciles with RevenueCat. */
class RevenueCatBillingGateway(
    private val context: Context,
    private val publicApiKey: String = BuildConfig.REVENUECAT_PUBLIC_API_KEY,
) : ProBillingGateway {
    private var activity = WeakReference<Activity>(null)
    private val updates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 16)
    override val purchaseUpdates = updates.asSharedFlow()
    private val identityMutex = Mutex()
    @Volatile private var awaitingPendingPurchase = false
    private var annualOption: SubscriptionOption? = null
    private var lifetimeProduct: StoreProduct? = null

    fun attach(activity: Activity) { this.activity = WeakReference(activity) }

    private fun sdk(): Purchases {
        check(publicApiKey.startsWith("goog_")) { "RevenueCat is not configured for this environment" }
        if (!Purchases.isConfigured) {
            Purchases.logLevel = LogLevel.ERROR
            Purchases.configure(
                PurchasesConfiguration.Builder(context.applicationContext, publicApiKey)
                    .automaticDeviceIdentifierCollectionEnabled(false)
                    .diagnosticsEnabled(false)
                    .showInAppMessagesAutomatically(false)
                    .build(),
            )
        }
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
            if (awaitingPendingPurchase && info.entitlements.active["aqualog_pro"] != null) {
                awaitingPendingPurchase = false
                updates.tryEmit(PlayPurchaseUpdate.CustomerInfoUpdated)
            }
        }
        return Purchases.sharedInstance
    }

    override suspend fun loadAnnualOffer(): AnnualProOffer {
        val products = products(ANNUAL_PRO_PRODUCT_ID, ProductType.SUBS)
        val option = products.flatMap { it.subscriptionOptions.orEmpty() }.firstOrNull { option ->
            val free = option.freePhase
            val paid = option.fullPricePhase
            free != null && free.billingPeriod.iso8601 in setOf("P14D", "P2W") && (free.billingCycleCount ?: 1) == 1 &&
                paid?.billingPeriod?.iso8601 == "P1Y" && paid.price.amountMicros > 0L && option.introPhase == null
        } ?: error("The annual RevenueCat product has no eligible 14-day trial")
        annualOption = option
        return AnnualProOffer(
            ANNUAL_PRO_PRODUCT_ID,
            requireNotNull(option.fullPricePhase).price.formatted,
            14,
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) }.timeInMillis,
        )
    }

    override suspend fun loadLifetimeOffer(): LifetimeProOffer {
        val product = products(LIFETIME_PRO_PRODUCT_ID, ProductType.INAPP).firstOrNull()
            ?: error("The lifetime RevenueCat product is unavailable")
        lifetimeProduct = product
        return LifetimeProOffer(LIFETIME_PRO_PRODUCT_ID, product.price.formatted)
    }

    override suspend fun launchAnnualPurchase(obfuscatedAccountId: String): BillingLaunchResult =
        launch(obfuscatedAccountId, annual = true)

    override suspend fun launchLifetimePurchase(obfuscatedAccountId: String): BillingLaunchResult =
        launch(obfuscatedAccountId, annual = false)

    private suspend fun launch(accountId: String, annual: Boolean): BillingLaunchResult = identityMutex.withLock {
        try {
            val currentActivity = activity.get() ?: return@withLock BillingLaunchResult.Failed("Return to Aquagenda to purchase")
            identify(accountId)
            // Recheck the offer after identification; never silently replace a promised trial with a paid base plan.
            val params = if (annual) {
                loadAnnualOffer()
                PurchaseParams.Builder(currentActivity, requireNotNull(annualOption)).build()
            } else {
                loadLifetimeOffer()
                PurchaseParams.Builder(currentActivity, requireNotNull(lifetimeProduct)).build()
            }
            sdk().purchaseWith(
                params,
                onError = { error, cancelled ->
                    awaitingPendingPurchase = !cancelled && error.code == PurchasesErrorCode.PaymentPendingError
                    updates.tryEmit(when {
                        cancelled -> PlayPurchaseUpdate.Cancelled
                        error.code == PurchasesErrorCode.PaymentPendingError -> PlayPurchaseUpdate.Pending
                        else -> PlayPurchaseUpdate.Failed("RevenueCat purchase failed; try again")
                    })
                },
                onSuccess = { _, _ ->
                    awaitingPendingPurchase = false
                    updates.tryEmit(PlayPurchaseUpdate.CustomerInfoUpdated)
                },
            )
            BillingLaunchResult.Started
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BillingLaunchResult.Failed("RevenueCat purchase is unavailable; try again")
        }
    }

    override suspend fun restorePurchases(accountIdentifier: String): PlayPurchaseUpdate = identityMutex.withLock {
        try {
            identify(accountIdentifier)
            suspendCancellableCoroutine<PlayPurchaseUpdate> { continuation ->
                sdk().restorePurchasesWith(
                    onError = {
                        if (continuation.isActive) continuation.resume(PlayPurchaseUpdate.Failed("RevenueCat restore failed; try again"))
                    },
                    onSuccess = {
                        if (continuation.isActive) continuation.resume(PlayPurchaseUpdate.CustomerInfoUpdated)
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PlayPurchaseUpdate.Failed("RevenueCat restore is unavailable; try again")
        }
    }

    // Legacy interface entries cannot initiate an anonymous RevenueCat restore.
    override suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate = PlayPurchaseUpdate.Failed("Sign in before restoring")
    override suspend fun restoreLifetimePurchase(): PlayPurchaseUpdate = PlayPurchaseUpdate.Failed("Sign in before restoring")

    private suspend fun identify(accountId: String) {
        require(accountId.matches(Regex("[0-9a-f]{64}"))) { "Verified purchase identity is required" }
        val purchases = sdk()
        if (purchases.appUserID == accountId) return
        awaitingPendingPurchase = false
        suspendCancellableCoroutine<Unit> { continuation ->
            purchases.logIn(accountId, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onError(error: PurchasesError) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("RevenueCat identification failed"))
                }
            })
        }
    }

    private suspend fun products(productId: String, type: ProductType): List<StoreProduct> =
        suspendCancellableCoroutine { continuation ->
            sdk().getProductsWith(
                listOf(productId), type,
                onError = {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("RevenueCat product is unavailable"))
                },
                onGetStoreProducts = { products ->
                    val matching = products.filter { it.id.substringBefore(':') == productId && it.type == type }
                    if (continuation.isActive) continuation.resume(matching)
                },
            )
        }
}
