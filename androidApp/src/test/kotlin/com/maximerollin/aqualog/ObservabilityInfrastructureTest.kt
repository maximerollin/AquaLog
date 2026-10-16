package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AnalyticsPayload
import com.maximerollin.aqualog.shared.AnalyticsValue
import com.maximerollin.aqualog.shared.RawTechnicalFailure
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservabilityInfrastructureTest {
    @Test
    fun `adapters are disabled unless complete safe configuration is present`() {
        val empty = configuration()
        assertFalse(empty.hasPostHog)
        assertFalse(empty.hasFirebase)
        assertFalse(empty.hasFeedback)

        val configured = configuration(
            postHogApiKey = "phc_test",
            postHogHost = "https://eu.i.posthog.com",
            firebaseApplicationId = "1:123:android:abc",
            firebaseApiKey = "test-api-key",
            firebaseProjectId = "aqualog-test",
            feedbackUrl = "https://example.supabase.co/functions/v1/submit-feedback",
        )
        assertTrue(configured.hasPostHog)
        assertTrue(configured.hasFirebase)
        assertTrue(configured.hasFeedback)
        assertFalse(configured.copy(postHogHost = "http://collector.example.com").hasPostHog)
        assertTrue(configured.copy(feedbackUrl = "http://10.0.2.2:54321/functions/v1/submit-feedback").hasFeedback)
        assertFalse(ObservabilityConfiguration.fromBuildConfig("http://127.0.0.1:54321", "").hasFeedback)
        assertTrue(ObservabilityConfiguration.fromBuildConfig("http://127.0.0.1:54321", "anon-key").hasFeedback)
    }

    @Test
    fun `PostHog sink forwards only a validated closed payload`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val sink = PostHogAnalyticsSink { name, properties -> captured += name to properties }

        sink.send(
            AnalyticsPayload(
                "entitlement_confirmed",
                mapOf("active" to AnalyticsValue.Flag(true), "product" to AnalyticsValue.Category("annual")),
            ),
        )

        assertEquals(
            listOf("entitlement_confirmed" to mapOf<String, Any>("active" to true, "product" to "annual")),
            captured,
        )
        assertFailsWith<IllegalArgumentException> {
            sink.send(
                AnalyticsPayload(
                    "session_saved",
                    mapOf("note" to AnalyticsValue.Category("private text")),
                ),
            )
        }
    }

    @Test
    fun `Crashlytics sink forwards only closed technical context`() {
        val captured = mutableListOf<RawTechnicalFailure>()
        val sink = FirebaseTechnicalFailureSink(captured::add)
        val valid = RawTechnicalFailure("non_fatal", mapOf("component" to "sync", "sync_category" to "network"))

        sink.record(valid)

        assertEquals(listOf(valid), captured)
        assertFailsWith<IllegalArgumentException> {
            sink.record(RawTechnicalFailure("crash", mapOf("email" to "owner@example.com")))
        }
    }

    @Test
    fun `feedback adapter sends only the voluntary message with the authenticated session`() = runTest {
        val requests = mutableListOf<Triple<String, String, String>>()
        val gateway = AuthenticatedFeedbackGateway(
            endpoint = "https://example.supabase.co/functions/v1/submit-feedback",
            sessionProvider = object : SyncSessionProvider {
                override suspend fun currentSession() = SyncSession("account-1", "access-token")
            },
            transport = FeedbackTransport { endpoint, token, message ->
                requests += Triple(endpoint, token, message)
                true
            },
        )

        assertTrue(gateway.submit(VoluntaryFeedback("Please improve reminders")))
        assertEquals(
            listOf(
                Triple(
                    "https://example.supabase.co/functions/v1/submit-feedback",
                    "access-token",
                    "Please improve reminders",
                ),
            ),
            requests,
        )
    }

    private fun configuration(
        postHogApiKey: String = "",
        postHogHost: String = "",
        firebaseApplicationId: String = "",
        firebaseApiKey: String = "",
        firebaseProjectId: String = "",
        feedbackUrl: String = "",
    ) = ObservabilityConfiguration(
        postHogApiKey,
        postHogHost,
        firebaseApplicationId,
        firebaseApiKey,
        firebaseProjectId,
        feedbackUrl,
    )
}
