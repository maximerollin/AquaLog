package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.PurchaseVerificationResult
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SupabaseProPurchaseVerifierTest {
    @Test
    fun `server verified lifetime entitlement is active without a local expiry`() = runTest {
        val transport = RecordingProPurchaseServerTransport(
            entitlementResponses = ArrayDeque(
                listOf(
                    ProPurchaseServerResponse.Success(
                        ServerProEntitlement(
                            active = true,
                            productId = LIFETIME_PRO_PRODUCT_ID,
                            validUntilEpochMillis = null,
                        ),
                    ),
                ),
            ),
        )
        val verifier = SupabaseProPurchaseVerifier(
            configuredSupabase,
            RecordingSessionProvider(SyncSession("account-1", "access-token"), null),
            transport,
        )

        val result = verifier.loadEntitlement()

        assertIs<PurchaseVerificationResult.Verified>(result)
        assertEquals(LIFETIME_PRO_PRODUCT_ID, result.entitlement.productId)
        assertEquals(null, result.entitlement.validUntilEpochMillis)
    }

    @Test
    fun `an unauthorized entitlement request refreshes once and replays with the new token`() = runTest {
        val sessions = RecordingSessionProvider(
            current = SyncSession("account-1", "expired-access"),
            refreshed = SyncSession("account-1", "fresh-access"),
        )
        val transport = RecordingProPurchaseServerTransport(
            entitlementResponses = ArrayDeque(
                listOf(
                    ProPurchaseServerResponse.Unauthorized,
                    ProPurchaseServerResponse.Success(
                        ServerProEntitlement(
                            active = true,
                            productId = ANNUAL_PRO_PRODUCT_ID,
                            validUntilEpochMillis = 1_824_120_000_000,
                        ),
                    ),
                ),
            ),
        )
        val verifier = SupabaseProPurchaseVerifier(configuredSupabase, sessions, transport)

        val result = verifier.loadEntitlement()

        assertIs<PurchaseVerificationResult.Verified>(result)
        assertEquals(listOf("expired-access", "fresh-access"), transport.accessTokens)
        assertEquals(1, sessions.refreshCount)
        assertEquals(0, sessions.reauthenticationCount)
    }

    @Test
    fun `a rejected refresh requires visible authentication without retrying`() = runTest {
        val sessions = RecordingSessionProvider(
            current = SyncSession("account-1", "expired-access"),
            refreshed = null,
        )
        val transport = RecordingProPurchaseServerTransport(
            entitlementResponses = ArrayDeque(listOf(ProPurchaseServerResponse.Unauthorized)),
        )
        val verifier = SupabaseProPurchaseVerifier(configuredSupabase, sessions, transport)

        val result = verifier.loadEntitlement()

        assertEquals(PurchaseVerificationResult.AuthenticationRequired, result)
        assertEquals(listOf("expired-access"), transport.accessTokens)
        assertEquals(1, sessions.refreshCount)
        assertEquals(1, sessions.reauthenticationCount)
    }

    @Test
    fun `a second unauthorized response requires authentication after one replay`() = runTest {
        val sessions = RecordingSessionProvider(
            current = SyncSession("account-1", "expired-access"),
            refreshed = SyncSession("account-1", "fresh-access"),
        )
        val transport = RecordingProPurchaseServerTransport(
            entitlementResponses = ArrayDeque(
                listOf(
                    ProPurchaseServerResponse.Unauthorized,
                    ProPurchaseServerResponse.Unauthorized,
                ),
            ),
        )
        val verifier = SupabaseProPurchaseVerifier(configuredSupabase, sessions, transport)

        val result = verifier.loadEntitlement()

        assertEquals(PurchaseVerificationResult.AuthenticationRequired, result)
        assertEquals(listOf("expired-access", "fresh-access"), transport.accessTokens)
        assertEquals(1, sessions.refreshCount)
        assertEquals(1, sessions.reauthenticationCount)
    }

    @Test
    fun `Play server active state is authoritative instead of the Android clock`() = runTest {
        val sessions = RecordingSessionProvider(
            current = SyncSession("account-1", "access-token"),
            refreshed = null,
        )
        val transport = RecordingProPurchaseServerTransport(
            entitlementResponses = ArrayDeque(
                listOf(
                    ProPurchaseServerResponse.Success(
                        ServerProEntitlement(
                            active = true,
                            productId = ANNUAL_PRO_PRODUCT_ID,
                            validUntilEpochMillis = 1L,
                        ),
                    ),
                ),
            ),
        )

        val result = SupabaseProPurchaseVerifier(configuredSupabase, sessions, transport)
            .loadEntitlement()

        assertIs<PurchaseVerificationResult.Verified>(result)
        assertEquals(1L, result.entitlement.validUntilEpochMillis)
    }

    private companion object {
        val configuredSupabase = SupabaseConfiguration("https://project.example", "anon-key")
    }
}

private class RecordingSessionProvider(
    private val current: SyncSession?,
    private val refreshed: SyncSession?,
) : SyncSessionProvider {
    var refreshCount = 0
    var reauthenticationCount = 0

    override suspend fun currentSession(): SyncSession? = current

    override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
        refreshCount += 1
        return refreshed
    }

    override suspend fun requireReauthentication() {
        reauthenticationCount += 1
    }
}

private class RecordingProPurchaseServerTransport(
    private val entitlementResponses: ArrayDeque<ProPurchaseServerResponse<ServerProEntitlement>>,
) : ProPurchaseServerTransport {
    val accessTokens = mutableListOf<String>()

    override suspend fun loadPurchaseAccountIdentity(accessToken: String):
        ProPurchaseServerResponse<String> = error("not used")

    override suspend fun loadEntitlement(accessToken: String):
        ProPurchaseServerResponse<ServerProEntitlement> {
        accessTokens += accessToken
        return entitlementResponses.removeFirst()
    }

    override suspend fun verify(
        accessToken: String,
        productId: String,
        purchaseToken: String,
    ): ProPurchaseServerResponse<ServerProEntitlement> = error("not used")
}
