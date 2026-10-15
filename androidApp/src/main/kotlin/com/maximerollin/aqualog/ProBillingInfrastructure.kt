package com.maximerollin.aqualog

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.BillingLaunchResult
import com.maximerollin.aqualog.shared.PlayPurchaseUpdate
import com.maximerollin.aqualog.shared.ProBillingGateway
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.PurchaseAccountIdentityResult
import com.maximerollin.aqualog.shared.ProPurchaseVerifier
import com.maximerollin.aqualog.shared.PurchaseVerificationResult
import com.maximerollin.aqualog.shared.SyncSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Calendar
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GooglePlayBillingGateway(
    context: Context,
) : ProBillingGateway, PurchasesUpdatedListener {
    private var activity = WeakReference<Activity>(null)
    private val mutablePurchaseUpdates = MutableSharedFlow<PlayPurchaseUpdate>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = mutablePurchaseUpdates

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    override suspend fun loadAnnualOffer(): AnnualProOffer {
        ensureConnected()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ANNUAL_PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val details = suspendCancellableCoroutine<ProductDetails> { continuation ->
            billingClient.queryProductDetailsAsync(params) { result, response ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                val detail = response.productDetailsList.firstOrNull()
                if (result.responseCode == BillingClient.BillingResponseCode.OK && detail != null) {
                    continuation.resume(detail)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(playMessage(result, "Annual Pro offer unavailable")),
                    )
                }
            }
        }
        val offer = details.subscriptionOfferDetails
            ?.firstOrNull { candidate ->
                candidate.pricingPhases.pricingPhaseList.any { phase ->
                    phase.priceAmountMicros == 0L && phase.billingPeriod == TRIAL_PERIOD
                }
            }
            ?: error("The Google Play annual Pro offer has no 14-day trial")
        val recurring = offer.pricingPhases.pricingPhaseList.lastOrNull { phase ->
            phase.priceAmountMicros > 0L && phase.billingPeriod == ANNUAL_PERIOD
        } ?: error("The Google Play annual Pro offer has no annual price")
        productDetails = details
        offerToken = offer.offerToken
        return AnnualProOffer(
            productId = details.productId,
            formattedPrice = recurring.formattedPrice,
            trialDays = TRIAL_DAYS,
            formattedBillingDate = formattedDateAfterTrial(),
        )
    }

    override suspend fun launchAnnualPurchase(obfuscatedAccountId: String): BillingLaunchResult {
        val purchaseActivity = activity.get()
            ?: return BillingLaunchResult.Failed("Return to AquaLog and try the purchase again")
        val details = productDetails ?: return BillingLaunchResult.Failed("Reload the Google Play offer and try again")
        val token = offerToken ?: return BillingLaunchResult.Failed("Reload the Google Play offer and try again")
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(token)
            .build()
        val result = billingClient.launchBillingFlow(
            purchaseActivity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(product))
                .setObfuscatedAccountId(obfuscatedAccountId)
                .build(),
        )
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLaunchResult.Started
        } else {
            BillingLaunchResult.Failed(playMessage(result, "Google Play could not start the purchase"))
        }
    }

    override suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate? {
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    continuation.resume(PlayPurchaseUpdate.Failed(playMessage(result, "Restore failed")))
                } else {
                    continuation.resume(purchases.firstOrNull(::isAnnualProPurchase)?.toUpdate())
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val update = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases
                ?.firstOrNull(::isAnnualProPurchase)
                ?.toUpdate()
                ?: PlayPurchaseUpdate.Failed("Google Play returned no annual Pro purchase")
            BillingClient.BillingResponseCode.USER_CANCELED -> PlayPurchaseUpdate.Cancelled
            else -> PlayPurchaseUpdate.Failed(playMessage(result, "Google Play purchase failed"))
        }
        mutablePurchaseUpdates.tryEmit(update)
    }

    fun close() = billingClient.endConnection()

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!continuation.isActive) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException(playMessage(result, "Google Play unavailable")),
                        )
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private fun Purchase.toUpdate(): PlayPurchaseUpdate = when (purchaseState) {
        Purchase.PurchaseState.PENDING -> PlayPurchaseUpdate.Pending
        Purchase.PurchaseState.PURCHASED -> PlayPurchaseUpdate.Purchased(
            productId = ANNUAL_PRO_PRODUCT_ID,
            purchaseToken = purchaseToken,
        )
        else -> PlayPurchaseUpdate.Failed("Google Play purchase is not complete")
    }

    private fun isAnnualProPurchase(purchase: Purchase): Boolean = ANNUAL_PRO_PRODUCT_ID in purchase.products

    private fun formattedDateAfterTrial(): String {
        val billingDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, TRIAL_DAYS) }.time
        return DateFormat.getDateInstance(DateFormat.LONG).format(billingDate)
    }

    private fun playMessage(result: BillingResult, fallback: String): String =
        result.debugMessage.takeIf(String::isNotBlank) ?: fallback

    private companion object {
        const val TRIAL_DAYS = 14
        const val TRIAL_PERIOD = "P14D"
        const val ANNUAL_PERIOD = "P1Y"
    }
}

internal class SupabaseProPurchaseVerifier(
    private val configuration: SupabaseConfiguration,
    private val sessionProvider: SyncSessionProvider,
    private val transport: ProPurchaseServerTransport = HttpProPurchaseServerTransport(configuration),
) : ProPurchaseVerifier {
    override suspend fun loadPurchaseAccountIdentity(): PurchaseAccountIdentityResult {
        if (!configuration.isConfigured) {
            return PurchaseAccountIdentityResult.Failed("Purchase verification is unavailable")
        }
        return when (val response = authorizedRequest(transport::loadPurchaseAccountIdentity)) {
            is ProPurchaseServerResponse.Success -> {
                val accountId = response.value
                if (accountId.isBlank() || accountId.length > 64) {
                    PurchaseAccountIdentityResult.Failed("Purchase account verification failed; try again")
                } else {
                    PurchaseAccountIdentityResult.Available(accountId)
                }
            }
            ProPurchaseServerResponse.Unauthorized -> PurchaseAccountIdentityResult.AuthenticationRequired
            is ProPurchaseServerResponse.Failure -> PurchaseAccountIdentityResult.Failed(response.reason)
        }
    }

    override suspend fun loadEntitlement(): PurchaseVerificationResult {
        if (!configuration.isConfigured) {
            return PurchaseVerificationResult.Failed("Pro status is unavailable")
        }
        return authorizedRequest(transport::loadEntitlement).toVerificationResult(
            fallback = "Pro status could not be refreshed",
        )
    }

    override suspend fun verify(productId: String, purchaseToken: String): PurchaseVerificationResult {
        if (!configuration.isConfigured) {
            return PurchaseVerificationResult.Failed("Purchase verification is unavailable")
        }
        return authorizedRequest { accessToken ->
            transport.verify(accessToken, productId, purchaseToken)
        }.toVerificationResult(fallback = "Receipt verification failed; try again")
    }

    private suspend fun <T> authorizedRequest(
        request: suspend (accessToken: String) -> ProPurchaseServerResponse<T>,
    ): ProPurchaseServerResponse<T> {
        var session = sessionProvider.currentSession() ?: return ProPurchaseServerResponse.Unauthorized
        var response = request(session.accessToken)
        if (response != ProPurchaseServerResponse.Unauthorized) return response
        session = sessionProvider.refreshSession(session) ?: run {
            sessionProvider.requireReauthentication()
            return ProPurchaseServerResponse.Unauthorized
        }
        response = request(session.accessToken)
        if (response == ProPurchaseServerResponse.Unauthorized) {
            sessionProvider.requireReauthentication()
        }
        return response
    }
}

internal data class ServerProEntitlement(
    val active: Boolean,
    val productId: String? = null,
    val validUntilEpochMillis: Long? = null,
)

internal sealed interface ProPurchaseServerResponse<out T> {
    data class Success<T>(val value: T) : ProPurchaseServerResponse<T>
    data object Unauthorized : ProPurchaseServerResponse<Nothing>
    data class Failure(val reason: String) : ProPurchaseServerResponse<Nothing>
}

internal interface ProPurchaseServerTransport {
    suspend fun loadPurchaseAccountIdentity(accessToken: String): ProPurchaseServerResponse<String>
    suspend fun loadEntitlement(accessToken: String): ProPurchaseServerResponse<ServerProEntitlement>
    suspend fun verify(
        accessToken: String,
        productId: String,
        purchaseToken: String,
    ): ProPurchaseServerResponse<ServerProEntitlement>
}

private class HttpProPurchaseServerTransport(
    private val configuration: SupabaseConfiguration,
) : ProPurchaseServerTransport {
    override suspend fun loadPurchaseAccountIdentity(accessToken: String) = post(
        accessToken = accessToken,
        body = JSONObject().put("action", "purchaseIdentity"),
    ) { response -> response.optString("obfuscatedAccountId") }

    override suspend fun loadEntitlement(accessToken: String) = post(
        accessToken = accessToken,
        body = JSONObject().put("action", "loadEntitlement"),
        parse = ::parseEntitlement,
    )

    override suspend fun verify(
        accessToken: String,
        productId: String,
        purchaseToken: String,
    ) = post(
        accessToken = accessToken,
        body = JSONObject()
            .put("productId", productId)
            .put("purchaseToken", purchaseToken),
        parse = ::parseEntitlement,
    )

    private suspend fun <T> post(
        accessToken: String,
        body: JSONObject,
        parse: (JSONObject) -> T,
    ): ProPurchaseServerResponse<T> = withContext(Dispatchers.IO) {
        val connection = verificationConnection(accessToken)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.outputStream.use { output -> output.write(body.toString().toByteArray()) }
            val status = connection.responseCode
            val responseBody = responseBody(connection, status)
            when {
                status == HttpURLConnection.HTTP_UNAUTHORIZED -> ProPurchaseServerResponse.Unauthorized
                status !in 200..299 -> ProPurchaseServerResponse.Failure(
                    JSONObject(responseBody).optString("error").ifBlank { "Purchase server request failed" },
                )
                else -> ProPurchaseServerResponse.Success(parse(JSONObject(responseBody)))
            }
        } catch (error: Exception) {
            ProPurchaseServerResponse.Failure(error.message ?: "Purchase server request failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun verificationConnection(accessToken: String): HttpURLConnection =
        (URL("${configuration.projectUrl}/functions/v1/verify-pro-purchase")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("apikey", configuration.anonymousKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }

    private fun parseEntitlement(response: JSONObject) = ServerProEntitlement(
        active = response.optBoolean("active", false),
        productId = response.optString("productId").takeIf(String::isNotBlank),
        validUntilEpochMillis = response.optLong("validUntilEpochMillis").takeIf { it > 0L },
    )

    private fun responseBody(connection: HttpURLConnection, status: Int): String =
        (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
}

private fun ProPurchaseServerResponse<ServerProEntitlement>.toVerificationResult(
    fallback: String,
): PurchaseVerificationResult = when (this) {
    ProPurchaseServerResponse.Unauthorized -> PurchaseVerificationResult.AuthenticationRequired
    is ProPurchaseServerResponse.Failure -> PurchaseVerificationResult.Failed(reason.ifBlank { fallback })
    is ProPurchaseServerResponse.Success -> {
        val entitlement = value
        if (
            !entitlement.active ||
            entitlement.productId != ANNUAL_PRO_PRODUCT_ID ||
            entitlement.validUntilEpochMillis == null
        ) {
            PurchaseVerificationResult.Rejected("Google Play did not confirm an active subscription")
        } else {
            PurchaseVerificationResult.Verified(
                ProEntitlement(ANNUAL_PRO_PRODUCT_ID, entitlement.validUntilEpochMillis),
            )
        }
    }
}
