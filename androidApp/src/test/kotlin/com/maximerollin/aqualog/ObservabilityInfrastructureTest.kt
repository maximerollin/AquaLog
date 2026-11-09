package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.AnalyticsPayload
import com.maximerollin.aqualog.shared.AnalyticsValue
import com.maximerollin.aqualog.shared.RawTechnicalFailure
import com.maximerollin.aqualog.shared.DisabledCrashReporter
import com.maximerollin.aqualog.shared.SyncSession
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.TechnicalComponent
import com.maximerollin.aqualog.shared.TechnicalFailure
import com.maximerollin.aqualog.shared.TechnicalFailureKind
import com.maximerollin.aqualog.shared.FeedbackKind
import com.maximerollin.aqualog.shared.FeedbackOutcome
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import org.json.JSONObject
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    fun `release channel distinguishes tracks without allowing free text`() {
        val captured = mutableListOf<Map<String, Any>>()
        val sink = PostHogAnalyticsSink("internal") { _, properties -> captured += properties }
        sink.send(AnalyticsPayload("review_requested"))
        assertEquals(listOf(mapOf<String, Any>("release_channel" to "internal")), captured)
        assertFailsWith<IllegalArgumentException> {
            PostHogAnalyticsSink("private@example.com") { _, _ -> error("must not send") }
                .send(AnalyticsPayload("review_requested"))
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
    fun `Crashlytics selection fails closed when initialization throws`() {
        val configured = configuration(
            firebaseApplicationId = "1:123:android:abc",
            firebaseApiKey = "test-api-key",
            firebaseProjectId = "aqualog-test",
        )

        val reporter = createCrashReporter(configured) { error("Firebase unavailable") }

        assertSame(DisabledCrashReporter, reporter)
    }

    @Test
    fun `Crashlytics selection enables the closed adapter after successful initialization`() {
        val captured = mutableListOf<RawTechnicalFailure>()
        val configured = configuration(
            firebaseApplicationId = "1:123:android:abc",
            firebaseApiKey = "test-api-key",
            firebaseProjectId = "aqualog-test",
        )
        val reporter = createCrashReporter(configured) { FirebaseTechnicalFailureClient(captured::add) }

        reporter.record(
            TechnicalFailure(
                kind = TechnicalFailureKind.NON_FATAL,
                component = TechnicalComponent.SYNC,
            ),
        )

        assertEquals(1, captured.size)
    }

    private class Post(val endpoint: String, val token: String?, val body: JSONObject)

    private class FakeInstallIds : FeedbackInstallIdStore {
        override fun getOrCreate() = "install-1"
        override fun clear() = Unit
    }

    private fun gateway(
        session: SyncSession?,
        refreshed: SyncSession? = null,
        codes: List<Int>,
        posts: MutableList<Post>,
        refreshes: MutableList<SyncSession> = mutableListOf(),
    ): SupabaseFeedbackGateway {
        val queue = codes.toMutableList()
        return SupabaseFeedbackGateway(
            endpoint = "https://example.supabase.co/functions/v1/submit-feedback",
            sessionProvider = object : SyncSessionProvider {
                override suspend fun currentSession() = session
                override suspend fun refreshSession(expiredSession: SyncSession): SyncSession? {
                    refreshes += expiredSession
                    return refreshed
                }
            },
            installIds = FakeInstallIds(),
            appVersion = "1.2.3",
            transport = FeedbackTransport { endpoint, token, body ->
                posts += Post(endpoint, token, body)
                queue.removeAt(0)
            },
        )
    }

    @Test
    fun `feedback with a session carries the token and no install id`() = runTest {
        val posts = mutableListOf<Post>()
        val outcome = gateway(SyncSession("a", "access-token"), codes = listOf(204), posts = posts)
            .submit(VoluntaryFeedback("Please improve reminders", FeedbackKind.IDEA, language = "fr"))

        assertEquals(FeedbackOutcome.SENT, outcome)
        assertEquals(1, posts.size)
        assertEquals("access-token", posts[0].token)
        assertEquals("https://example.supabase.co/functions/v1/submit-feedback", posts[0].endpoint)
        assertFalse(posts[0].body.has("installId"))
    }

    @Test
    fun `feedback without a session is anonymous with the install id`() = runTest {
        val posts = mutableListOf<Post>()
        val outcome = gateway(null, codes = listOf(204), posts = posts).submit(VoluntaryFeedback("Hello"))

        assertEquals(FeedbackOutcome.SENT, outcome)
        assertEquals(1, posts.size)
        assertNull(posts[0].token)
        assertEquals("install-1", posts[0].body.getString("installId"))
    }

    @Test
    fun `a refused token is refreshed once and retried`() = runTest {
        val posts = mutableListOf<Post>()
        val refreshes = mutableListOf<SyncSession>()
        val outcome = gateway(
            SyncSession("a", "old"),
            refreshed = SyncSession("a", "new"),
            codes = listOf(401, 204),
            posts = posts,
            refreshes = refreshes,
        ).submit(VoluntaryFeedback("Hello"))

        assertEquals(FeedbackOutcome.SENT, outcome)
        assertEquals(1, refreshes.size)
        assertEquals(listOf<String?>("old", "new"), posts.map { it.token })
        assertFalse(posts[1].body.has("installId"))
    }

    @Test
    fun `a token refused twice falls back to anonymous`() = runTest {
        val posts = mutableListOf<Post>()
        val outcome = gateway(
            SyncSession("a", "old"),
            refreshed = SyncSession("a", "new"),
            codes = listOf(401, 401, 204),
            posts = posts,
        ).submit(VoluntaryFeedback("Hello"))

        assertEquals(FeedbackOutcome.SENT, outcome)
        assertEquals(listOf<String?>("old", "new", null), posts.map { it.token })
        assertEquals("install-1", posts[2].body.getString("installId"))
    }

    @Test
    fun `a refused token without a refreshed session falls back to anonymous`() = runTest {
        val posts = mutableListOf<Post>()
        val outcome = gateway(SyncSession("a", "old"), refreshed = null, codes = listOf(401, 204), posts = posts)
            .submit(VoluntaryFeedback("Hello"))

        assertEquals(FeedbackOutcome.SENT, outcome)
        assertEquals(listOf<String?>("old", null), posts.map { it.token })
    }

    @Test
    fun `http codes map to outcomes`() = runTest {
        suspend fun outcome(code: Int) =
            gateway(null, codes = listOf(code), posts = mutableListOf()).submit(VoluntaryFeedback("Hello"))

        assertEquals(FeedbackOutcome.SENT, outcome(204))
        assertEquals(FeedbackOutcome.RATE_LIMITED, outcome(429))
        assertEquals(FeedbackOutcome.FAILED, outcome(-1))
        assertEquals(FeedbackOutcome.FAILED, outcome(500))
        assertEquals(FeedbackOutcome.FAILED, outcome(400))
    }

    @Test
    fun `the body carries only the allowed fields`() = runTest {
        val posts = mutableListOf<Post>()
        gateway(SyncSession("a", "t"), codes = listOf(204, 204), posts = posts).apply {
            submit(VoluntaryFeedback("Hello", FeedbackKind.SPECIES, speciesId = "neon-tetra", language = "fr"))
            submit(VoluntaryFeedback("Hello"))
        }

        val full = posts[0].body
        assertEquals(setOf("message", "kind", "speciesId", "appVersion", "language"), full.keys().asSequence().toSet())
        assertEquals("species", full.getString("kind"))
        assertEquals("neon-tetra", full.getString("speciesId"))
        assertEquals("1.2.3", full.getString("appVersion"))
        assertEquals("fr", full.getString("language"))

        val minimal = posts[1].body
        assertEquals(setOf("message", "kind", "appVersion"), minimal.keys().asSequence().toSet())
        assertEquals("other", minimal.getString("kind"))
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
