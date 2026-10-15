package com.maximerollin.aqualog.shared

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
    fun `voluntary feedback stays on its dedicated channel`() {
        val analytics = mutableListOf<AnalyticsPayload>()
        val feedback = mutableListOf<VoluntaryFeedback>()
        val coordinator = FeedbackCoordinator(
            gateway = FeedbackGateway(feedback::add),
        )

        coordinator.submit(VoluntaryFeedback("The task reminder was late"))

        assertEquals(listOf("The task reminder was late"), feedback.map(VoluntaryFeedback::message))
        assertTrue(analytics.isEmpty())
    }

    @Test
    fun `review eligibility waits for successful use and never asks a satisfaction question`() {
        val policy = ReviewEligibilityPolicy(
            minimumSuccessfulSessions = 3,
            minimumAgeMillis = 7 * 24 * 60 * 60 * 1_000L,
        )
        val firstSuccess = 1_000L

        assertFalse(policy.isEligible(ReviewHistory(2, firstSuccess, requested = false), firstSuccess + 8.days))
        assertFalse(policy.isEligible(ReviewHistory(3, firstSuccess, requested = false), firstSuccess + 6.days))
        assertTrue(policy.isEligible(ReviewHistory(3, firstSuccess, requested = false), firstSuccess + 8.days))
        assertFalse(policy.isEligible(ReviewHistory(4, firstSuccess, requested = true), firstSuccess + 9.days))
    }

    @Test
    fun `eligible successful use directly requests the native review flow once`() {
        var history = ReviewHistory(2, 1_000L, requested = false)
        var requests = 0
        val coordinator = ReviewCoordinator(
            policy = ReviewEligibilityPolicy(minimumSuccessfulSessions = 3, minimumAgeMillis = 0),
            store = object : ReviewHistoryStore {
                override fun load() = history
                override fun save(updated: ReviewHistory) {
                    history = updated
                }
            },
            gateway = InAppReviewGateway { requests += 1 },
            currentTimeMillis = { 2_000L },
        )

        coordinator.onSuccessfulSession()
        coordinator.onSuccessfulSession()

        assertEquals(1, requests)
        assertTrue(history.requested)
        assertEquals(4, history.successfulSessionCount)
    }

    private val Int.days: Long get() = this * 24 * 60 * 60 * 1_000L
}
