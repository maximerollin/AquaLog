package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.ProPurchaseVerifier
import com.maximerollin.aqualog.shared.PurchaseAccountIdentityResult
import com.maximerollin.aqualog.shared.PurchaseVerificationResult
import com.maximerollin.aqualog.shared.SyncSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val isAnnual = entitlement.productId == ANNUAL_PRO_PRODUCT_ID &&
            entitlement.validUntilEpochMillis != null
        val isLifetime = entitlement.productId == LIFETIME_PRO_PRODUCT_ID &&
            entitlement.validUntilEpochMillis == null
        if (!entitlement.active || (!isAnnual && !isLifetime)) {
            PurchaseVerificationResult.Rejected("RevenueCat did not confirm an active Pro entitlement")
        } else {
            PurchaseVerificationResult.Verified(
                ProEntitlement(requireNotNull(entitlement.productId), entitlement.validUntilEpochMillis),
            )
        }
    }
}
