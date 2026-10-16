package com.maximerollin.aqualog.shared

enum class OnboardingMilestone(internal val wireValue: String) {
    STARTED("started"),
    PROFILE_SELECTED("profile_selected"),
    PRACTICE_COMPLETED("practice_completed"),
    COMPLETED("completed"),
}

enum class SessionUsage(internal val wireValue: String) {
    CREATED("created"),
    EDITED("edited"),
}

enum class TaskUsage(internal val wireValue: String) {
    CREATED("created"),
    COMPLETED("completed"),
    POSTPONED("postponed"),
    SKIPPED("skipped"),
}

enum class GraphPeriod(internal val wireValue: String) {
    DAYS_7("7_days"),
    DAYS_30("30_days"),
    DAYS_90("90_days"),
    ALL_HISTORY("all_history"),
}

enum class SyncOutcome(internal val wireValue: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
}

enum class AnalyticsSyncFailureCategory(internal val wireValue: String) {
    NETWORK("network"),
    CONFLICT("conflict"),
    SERVER("server"),
    UNKNOWN("unknown"),
}

enum class PaywallSource(internal val wireValue: String) {
    ONBOARDING("onboarding"),
    SETTINGS("settings"),
    PRO_FEATURE("pro_feature"),
}

enum class ProProduct(internal val wireValue: String) {
    ANNUAL("annual"),
    LIFETIME("lifetime"),
}

sealed interface ProductEvent {
    data class Onboarding(val milestone: OnboardingMilestone) : ProductEvent
    data object FirstSessionSaved : ProductEvent
    data class SessionSaved(val usage: SessionUsage) : ProductEvent
    data class TaskUsed(val usage: TaskUsage) : ProductEvent
    data class GraphViewed(val period: GraphPeriod) : ProductEvent
    data object ExportCompleted : ProductEvent
    data class SyncFinished(
        val outcome: SyncOutcome,
        val failureCategory: AnalyticsSyncFailureCategory? = null,
    ) : ProductEvent {
        init {
            require((outcome == SyncOutcome.FAILED) == (failureCategory != null)) {
                "Failed synchronization needs one closed failure category; success has none"
            }
        }
    }
    data class PaywallViewed(val source: PaywallSource) : ProductEvent
    data class PurchaseConfirmed(val product: ProProduct) : ProductEvent
    data class EntitlementConfirmed(val active: Boolean, val product: ProProduct) : ProductEvent
}

sealed interface AnalyticsValue {
    data class Flag(val value: Boolean) : AnalyticsValue
    data class Category(val value: String) : AnalyticsValue
}

data class AnalyticsPayload(
    val name: String,
    val properties: Map<String, AnalyticsValue> = emptyMap(),
)

fun interface AnalyticsPayloadSink {
    fun send(payload: AnalyticsPayload)
}

interface ProductTelemetry {
    fun record(event: ProductEvent)
}

class AllowlistedAnalyticsAdapter(
    private val sink: AnalyticsPayloadSink,
) : AnalyticsPayloadSink {
    override fun send(payload: AnalyticsPayload) {
        require(ProductAnalyticsSchema.isAllowed(payload)) {
            "Analytics payload is not part of AquaLog's privacy allowlist"
        }
        sink.send(payload)
    }
}

class AllowlistedProductTelemetry(
    private val adapter: AnalyticsPayloadSink,
    private val enabled: Boolean = false,
) : ProductTelemetry {
    override fun record(event: ProductEvent) {
        if (enabled) adapter.send(ProductAnalyticsSchema.encode(event))
    }
}

object DisabledProductTelemetry : ProductTelemetry {
    override fun record(event: ProductEvent) = Unit
}

object ProductAnalyticsSchema {
    private data class PropertyRule(
        val required: Boolean = true,
        val allowedCategories: Set<String>? = null,
        val type: ValueType = ValueType.CATEGORY,
    )

    private enum class ValueType { FLAG, CATEGORY }

    private val rules = mapOf(
        "onboarding_progress" to mapOf(
            "milestone" to PropertyRule(allowedCategories = OnboardingMilestone.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "first_session_saved" to emptyMap(),
        "session_saved" to mapOf(
            "usage" to PropertyRule(allowedCategories = SessionUsage.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "task_used" to mapOf(
            "usage" to PropertyRule(allowedCategories = TaskUsage.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "graph_viewed" to mapOf(
            "period" to PropertyRule(allowedCategories = GraphPeriod.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "export_completed" to emptyMap(),
        "sync_finished" to mapOf(
            "outcome" to PropertyRule(allowedCategories = SyncOutcome.entries.mapTo(mutableSetOf()) { it.wireValue }),
            "failure_category" to PropertyRule(
                required = false,
                allowedCategories = AnalyticsSyncFailureCategory.entries.mapTo(mutableSetOf()) { it.wireValue },
            ),
        ),
        "paywall_viewed" to mapOf(
            "source" to PropertyRule(allowedCategories = PaywallSource.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "purchase_confirmed" to mapOf(
            "product" to PropertyRule(allowedCategories = ProProduct.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
        "entitlement_confirmed" to mapOf(
            "active" to PropertyRule(type = ValueType.FLAG),
            "product" to PropertyRule(allowedCategories = ProProduct.entries.mapTo(mutableSetOf()) { it.wireValue }),
        ),
    )

    fun encode(event: ProductEvent): AnalyticsPayload {
        val payload = when (event) {
            is ProductEvent.Onboarding -> AnalyticsPayload(
                "onboarding_progress",
                mapOf("milestone" to AnalyticsValue.Category(event.milestone.wireValue)),
            )
            ProductEvent.FirstSessionSaved -> AnalyticsPayload("first_session_saved")
            is ProductEvent.SessionSaved -> AnalyticsPayload(
                "session_saved",
                mapOf("usage" to AnalyticsValue.Category(event.usage.wireValue)),
            )
            is ProductEvent.TaskUsed -> AnalyticsPayload(
                "task_used",
                mapOf("usage" to AnalyticsValue.Category(event.usage.wireValue)),
            )
            is ProductEvent.GraphViewed -> AnalyticsPayload(
                "graph_viewed",
                mapOf("period" to AnalyticsValue.Category(event.period.wireValue)),
            )
            ProductEvent.ExportCompleted -> AnalyticsPayload("export_completed")
            is ProductEvent.SyncFinished -> AnalyticsPayload(
                "sync_finished",
                buildMap {
                    put("outcome", AnalyticsValue.Category(event.outcome.wireValue))
                    event.failureCategory?.let {
                        put("failure_category", AnalyticsValue.Category(it.wireValue))
                    }
                },
            )
            is ProductEvent.PaywallViewed -> AnalyticsPayload(
                "paywall_viewed",
                mapOf("source" to AnalyticsValue.Category(event.source.wireValue)),
            )
            is ProductEvent.PurchaseConfirmed -> AnalyticsPayload(
                "purchase_confirmed",
                mapOf("product" to AnalyticsValue.Category(event.product.wireValue)),
            )
            is ProductEvent.EntitlementConfirmed -> AnalyticsPayload(
                "entitlement_confirmed",
                mapOf(
                    "active" to AnalyticsValue.Flag(event.active),
                    "product" to AnalyticsValue.Category(event.product.wireValue),
                ),
            )
        }
        check(isAllowed(payload)) { "Product event encoder produced a forbidden payload" }
        return payload
    }

    fun isAllowed(payload: AnalyticsPayload): Boolean {
        val eventRules = rules[payload.name] ?: return false
        if (!eventRules.keys.containsAll(payload.properties.keys)) return false
        if (eventRules.any { (key, rule) -> rule.required && key !in payload.properties }) return false
        if (!payload.properties.all { (key, value) ->
            val rule = eventRules[key] ?: return@all false
            when {
                rule.type == ValueType.FLAG -> value is AnalyticsValue.Flag
                rule.type == ValueType.CATEGORY -> {
                    value is AnalyticsValue.Category && value.value in requireNotNull(rule.allowedCategories)
                }
                else -> false
            }
        }) return false
        if (payload.name == "sync_finished") {
            val outcome = (payload.properties["outcome"] as AnalyticsValue.Category).value
            val hasFailureCategory = "failure_category" in payload.properties
            if ((outcome == SyncOutcome.FAILED.wireValue) != hasFailureCategory) return false
        }
        return true
    }

}
