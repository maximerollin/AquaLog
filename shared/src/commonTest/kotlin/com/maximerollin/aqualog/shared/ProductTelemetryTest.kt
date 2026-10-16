package com.maximerollin.aqualog.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductTelemetryTest {
    @Test
    fun `the central schema emits only the documented product events and properties`() {
        val payloads = listOf(
            ProductEvent.Onboarding(OnboardingMilestone.STARTED),
            ProductEvent.FirstSessionSaved,
            ProductEvent.SessionSaved(SessionUsage.CREATED),
            ProductEvent.TaskUsed(TaskUsage.COMPLETED),
            ProductEvent.GraphViewed(GraphPeriod.DAYS_30),
            ProductEvent.ExportCompleted,
            ProductEvent.SyncFinished(SyncOutcome.FAILED, AnalyticsSyncFailureCategory.NETWORK),
            ProductEvent.PaywallViewed(PaywallSource.SETTINGS),
            ProductEvent.PurchaseConfirmed(ProProduct.ANNUAL),
            ProductEvent.EntitlementConfirmed(active = true, product = ProProduct.ANNUAL),
        ).map(ProductAnalyticsSchema::encode)

        assertEquals(
            listOf(
                "onboarding_progress",
                "first_session_saved",
                "session_saved",
                "task_used",
                "graph_viewed",
                "export_completed",
                "sync_finished",
                "paywall_viewed",
                "purchase_confirmed",
                "entitlement_confirmed",
            ),
            payloads.map(AnalyticsPayload::name),
        )
        assertTrue(payloads.all(ProductAnalyticsSchema::isAllowed))
    }

    @Test
    fun `the defensive adapter fails closed for personal or free-form payloads`() {
        val sent = mutableListOf<AnalyticsPayload>()
        val adapter = AllowlistedAnalyticsAdapter(sent::add)

        listOf(
            AnalyticsPayload(
                name = "session_saved",
                properties = mapOf("water_value" to AnalyticsValue.Category("24")),
            ),
            AnalyticsPayload(
                name = "paywall_viewed",
                properties = mapOf("source" to AnalyticsValue.Category("person@example.com")),
            ),
            AnalyticsPayload(
                name = "sync_finished",
                properties = mapOf("error" to AnalyticsValue.Category("aquarium note")),
            ),
            AnalyticsPayload(name = "unknown_event"),
        ).forEach { payload ->
            assertFailsWith<IllegalArgumentException> { adapter.send(payload) }
        }

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `every forbidden content key and wrong value type is rejected before transport`() {
        val sent = mutableListOf<AnalyticsPayload>()
        val adapter = AllowlistedAnalyticsAdapter(sent::add)
        val forbiddenKeys = listOf(
            "water_value",
            "note",
            "observation",
            "aquarium_name",
            "email",
            "photo",
            "free_text",
        )

        forbiddenKeys.forEach { forbiddenKey ->
            assertFailsWith<IllegalArgumentException> {
                adapter.send(
                    AnalyticsPayload(
                        name = "session_saved",
                        properties = mapOf(
                            "usage" to AnalyticsValue.Category("created"),
                            forbiddenKey to AnalyticsValue.Category("private"),
                        ),
                    ),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.send(
                AnalyticsPayload(
                    name = "paywall_viewed",
                    properties = mapOf("source" to AnalyticsValue.Flag(true)),
                ),
            )
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `synchronization outcomes accept only coherent technical failure context`() {
        val adapter = AllowlistedAnalyticsAdapter { error("No invalid payload may reach transport: $it") }

        assertFailsWith<IllegalArgumentException> {
            adapter.send(
                AnalyticsPayload(
                    name = "sync_finished",
                    properties = mapOf("outcome" to AnalyticsValue.Category("failed")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            adapter.send(
                AnalyticsPayload(
                    name = "sync_finished",
                    properties = mapOf(
                        "outcome" to AnalyticsValue.Category("succeeded"),
                        "failure_category" to AnalyticsValue.Category("network"),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProductEvent.SyncFinished(SyncOutcome.FAILED)
        }
        assertFailsWith<IllegalArgumentException> {
            ProductEvent.SyncFinished(SyncOutcome.SUCCEEDED, AnalyticsSyncFailureCategory.NETWORK)
        }
    }

    @Test
    fun `enabled product telemetry sends the validated encoded payload`() {
        val sent = mutableListOf<AnalyticsPayload>()
        val telemetry = AllowlistedProductTelemetry(
            adapter = AllowlistedAnalyticsAdapter(sent::add),
            enabled = true,
        )

        telemetry.record(ProductEvent.PaywallViewed(PaywallSource.ONBOARDING))

        assertEquals(
            listOf(
                AnalyticsPayload(
                    name = "paywall_viewed",
                    properties = mapOf("source" to AnalyticsValue.Category("onboarding")),
                ),
            ),
            sent,
        )
    }

    @Test
    fun `product telemetry is disabled until an external integration is explicitly enabled`() {
        val sent = mutableListOf<AnalyticsPayload>()
        val telemetry = AllowlistedProductTelemetry(
            adapter = AllowlistedAnalyticsAdapter(sent::add),
            enabled = false,
        )

        telemetry.record(ProductEvent.FirstSessionSaved)

        assertTrue(sent.isEmpty())
    }
}
