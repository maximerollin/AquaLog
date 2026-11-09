package com.maximerollin.aqualog.shared

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrivacyServicesTest {
    @Test
    fun `crash and ANR context accepts technical categories only`() {
        val reports = mutableListOf<RawTechnicalFailure>()
        val reporter = AllowlistedCrashReporter(
            AllowlistedCrashAdapter(reports::add),
            enabled = true,
        )

        reporter.record(
            TechnicalFailure(
                kind = TechnicalFailureKind.ANR,
                component = TechnicalComponent.SYNC,
                syncCategory = AnalyticsSyncFailureCategory.NETWORK,
            ),
        )

        assertEquals(1, reports.size)
        assertFailsWith<IllegalArgumentException> {
            AllowlistedCrashAdapter(reports::add).record(
                RawTechnicalFailure(
                    kind = "crash",
                    context = mapOf("aquarium_name" to "Living room"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AllowlistedCrashAdapter(reports::add).record(
                RawTechnicalFailure(
                    kind = "anr",
                    context = mapOf("component" to "user@example.com"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AllowlistedCrashAdapter(reports::add).record(
                RawTechnicalFailure(
                    kind = "non_fatal",
                    context = mapOf("component" to "billing", "sync_category" to "network"),
                ),
            )
        }
    }

    @Test
    fun `voluntary feedback stays on its dedicated channel`() = runTest {
        val analytics = mutableListOf<AnalyticsPayload>()
        val feedback = mutableListOf<VoluntaryFeedback>()
        val coordinator = FeedbackCoordinator(
            gateway = FeedbackGateway { feedback += it; FeedbackOutcome.SENT },
        )

        coordinator.submit(VoluntaryFeedback("The task reminder was late"))

        assertEquals(listOf("The task reminder was late"), feedback.map(VoluntaryFeedback::message))
        assertTrue(analytics.isEmpty())
    }

    @Test
    fun `disabled feedback gateway reports unavailable`() = runTest {
        assertEquals(FeedbackOutcome.UNAVAILABLE, DisabledFeedbackGateway.submit(VoluntaryFeedback("Hello")))
    }

    private class ReviewFixture(
        var history: ReviewHistory = ReviewHistory(0, false, null, false),
        var now: Long = 1_000L,
    ) {
        var requests = 0
        val coordinator = ReviewCoordinator(
            policy = ReviewEligibilityPolicy(),
            store = object : ReviewHistoryStore {
                override fun load() = history
                override fun save(updated: ReviewHistory) {
                    history = updated
                }
            },
            gateway = InAppReviewGateway { requests += 1 },
            currentTimeMillis = { now },
        )
    }

    @Test
    fun `review becomes pending on the fifth calm session with a discovery step`() {
        val fixture = ReviewFixture()

        repeat(4) { fixture.coordinator.onNewSessionSaved(calm = true) }
        assertFalse(fixture.history.pending)
        assertEquals(ReviewPrompt.NONE, fixture.coordinator.pendingPrompt())

        fixture.coordinator.onNewSessionSaved(calm = true)
        assertTrue(fixture.history.pending)
        assertEquals(ReviewPrompt.DISCOVERY, fixture.coordinator.pendingPrompt())
        assertEquals(0, fixture.requests)
    }

    @Test
    fun `a session with advice does not trigger the review, the next calm one does`() {
        val fixture = ReviewFixture()

        repeat(4) { fixture.coordinator.onNewSessionSaved(calm = true) }
        fixture.coordinator.onNewSessionSaved(calm = false)
        assertFalse(fixture.history.pending)

        fixture.coordinator.onNewSessionSaved(calm = true)
        assertTrue(fixture.history.pending)
    }

    @Test
    fun `sessions that are never calm never make the review pending`() {
        val fixture = ReviewFixture()

        repeat(20) { fixture.coordinator.onNewSessionSaved(calm = false) }

        assertFalse(fixture.history.pending)
        assertEquals(20, fixture.history.newSessionCount)
    }

    @Test
    fun `after a declined discovery the review waits 90 days then asks directly`() {
        val fixture = ReviewFixture(ReviewHistory(4, false, null, false))
        fixture.coordinator.onNewSessionSaved(calm = true)

        fixture.coordinator.onDiscoveryAnswered(leaveReview = false)
        assertEquals(0, fixture.requests)
        assertEquals(ReviewPrompt.NONE, fixture.coordinator.pendingPrompt())
        assertTrue(fixture.history.discoverySeen)
        assertEquals(1_000L, fixture.history.lastRequestedAtEpochMillis)

        fixture.now = 1_000L + 89.days
        fixture.coordinator.onNewSessionSaved(calm = true)
        assertFalse(fixture.history.pending)

        fixture.now = 1_000L + 90.days
        fixture.coordinator.onNewSessionSaved(calm = true)
        assertTrue(fixture.history.pending)
        assertEquals(ReviewPrompt.DIRECT, fixture.coordinator.pendingPrompt())
    }

    @Test
    fun `accepting the discovery or a direct request calls the native flow once each`() {
        val fixture = ReviewFixture(ReviewHistory(5, true, null, false))
        fixture.coordinator.onDiscoveryAnswered(leaveReview = true)
        assertEquals(1, fixture.requests)
        assertFalse(fixture.history.pending)

        fixture.history = fixture.history.copy(pending = true)
        fixture.coordinator.requestDirect()
        assertEquals(2, fixture.requests)
        assertFalse(fixture.history.pending)
        assertEquals(1_000L, fixture.history.lastRequestedAtEpochMillis)
    }

    @Test
    fun `a legacy requested flag becomes a request date and the discovery is still to come`() {
        val history = storedReviewHistory(
            newSessionCount = 3,
            pending = false,
            lastRequestedAtEpochMillis = null,
            discoverySeen = false,
            legacyRequested = true,
            nowEpochMillis = 1_000L,
        )
        assertEquals(1_000L, history.lastRequestedAtEpochMillis)

        val fixture = ReviewFixture(history.copy(newSessionCount = 10), now = 1_000L + 89.days)
        fixture.coordinator.onNewSessionSaved(calm = true)
        assertFalse(fixture.history.pending)

        fixture.now = 1_000L + 90.days
        fixture.coordinator.onNewSessionSaved(calm = true)
        assertTrue(fixture.history.pending)
        assertEquals(ReviewPrompt.DISCOVERY, fixture.coordinator.pendingPrompt())
    }

    private val Int.days: Long get() = this * 24 * 60 * 60 * 1_000L
}
