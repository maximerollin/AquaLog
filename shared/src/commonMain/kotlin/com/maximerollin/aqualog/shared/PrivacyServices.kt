package com.maximerollin.aqualog.shared

enum class TechnicalFailureKind(internal val wireValue: String) {
    CRASH("crash"),
    ANR("anr"),
    NON_FATAL("non_fatal"),
}

enum class TechnicalComponent(internal val wireValue: String) {
    APP_START("app_start"),
    DATABASE("database"),
    SYNC("sync"),
    BILLING("billing"),
    EXPORT("export"),
    REVIEW("review"),
}

data class TechnicalFailure(
    val kind: TechnicalFailureKind,
    val component: TechnicalComponent,
    val syncCategory: AnalyticsSyncFailureCategory? = null,
)

data class RawTechnicalFailure(
    val kind: String,
    val context: Map<String, String>,
)

fun interface TechnicalFailureSink {
    fun record(failure: RawTechnicalFailure)
}

interface CrashReporter {
    fun record(failure: TechnicalFailure)
}

class AllowlistedCrashAdapter(
    private val sink: TechnicalFailureSink,
) : TechnicalFailureSink {
    override fun record(failure: RawTechnicalFailure) {
        require(TechnicalFailureSchema.isAllowed(failure)) {
            "Crash context is not part of AquaLog's technical allowlist"
        }
        sink.record(failure)
    }
}

class AllowlistedCrashReporter(
    private val adapter: TechnicalFailureSink,
    private val enabled: Boolean = false,
) : CrashReporter {
    override fun record(failure: TechnicalFailure) {
        if (enabled) adapter.record(TechnicalFailureSchema.encode(failure))
    }
}

object DisabledCrashReporter : CrashReporter {
    override fun record(failure: TechnicalFailure) = Unit
}

object TechnicalFailureSchema {
    private val kinds = TechnicalFailureKind.entries.mapTo(mutableSetOf()) { it.wireValue }
    private val components = TechnicalComponent.entries.mapTo(mutableSetOf()) { it.wireValue }
    private val syncCategories = AnalyticsSyncFailureCategory.entries.mapTo(mutableSetOf()) { it.wireValue }

    fun encode(failure: TechnicalFailure): RawTechnicalFailure = RawTechnicalFailure(
        kind = failure.kind.wireValue,
        context = buildMap {
            put("component", failure.component.wireValue)
            failure.syncCategory?.let { put("sync_category", it.wireValue) }
        },
    ).also { check(isAllowed(it)) }

    fun isAllowed(failure: RawTechnicalFailure): Boolean {
        if (failure.kind !in kinds) return false
        if (failure.context.keys.any { it !in setOf("component", "sync_category") }) return false
        if (failure.context["component"] !in components) return false
        val syncCategory = failure.context["sync_category"]
        if (syncCategory != null && failure.context["component"] != TechnicalComponent.SYNC.wireValue) return false
        return syncCategory == null || syncCategory in syncCategories
    }
}

data class VoluntaryFeedback(val message: String) {
    init {
        require(message.isNotBlank()) { "Feedback cannot be blank" }
        require(message.length <= 2_000) { "Feedback is limited to 2,000 characters" }
    }
}

fun interface FeedbackGateway {
    fun submit(feedback: VoluntaryFeedback): Boolean
}

class FeedbackCoordinator(
    private val gateway: FeedbackGateway,
) {
    fun submit(feedback: VoluntaryFeedback): Boolean = gateway.submit(feedback)
}

object DisabledFeedbackGateway : FeedbackGateway {
    override fun submit(feedback: VoluntaryFeedback) = false
}

data class ReviewHistory(
    val successfulSessionCount: Int,
    val firstSuccessfulSessionAtEpochMillis: Long?,
    val requested: Boolean,
)

class ReviewEligibilityPolicy(
    private val minimumSuccessfulSessions: Int = 3,
    private val minimumAgeMillis: Long = 7 * 24 * 60 * 60 * 1_000L,
) {
    init {
        require(minimumSuccessfulSessions > 0)
        require(minimumAgeMillis >= 0)
    }

    fun isEligible(history: ReviewHistory, nowEpochMillis: Long): Boolean {
        val firstSuccess = history.firstSuccessfulSessionAtEpochMillis ?: return false
        return !history.requested &&
            history.successfulSessionCount >= minimumSuccessfulSessions &&
            nowEpochMillis - firstSuccess >= minimumAgeMillis
    }
}

interface ReviewHistoryStore {
    fun load(): ReviewHistory
    fun save(updated: ReviewHistory)
}

fun interface InAppReviewGateway {
    fun requestReview()
}

class ReviewCoordinator(
    private val policy: ReviewEligibilityPolicy,
    private val store: ReviewHistoryStore,
    private val gateway: InAppReviewGateway,
    private val currentTimeMillis: () -> Long,
) {
    fun onSuccessfulSession() {
        val now = currentTimeMillis()
        val previous = store.load()
        val updated = previous.copy(
            successfulSessionCount = previous.successfulSessionCount + 1,
            firstSuccessfulSessionAtEpochMillis = previous.firstSuccessfulSessionAtEpochMillis ?: now,
        )
        if (policy.isEligible(updated, now)) {
            store.save(updated.copy(requested = true))
            gateway.requestReview()
        } else {
            store.save(updated)
        }
    }
}
