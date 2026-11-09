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

enum class FeedbackKind(val wireValue: String) {
    BUG("bug"),
    TRANSLATION("translation"),
    SPECIES("species"),
    IDEA("idea"),
    OTHER("other"),
}

data class VoluntaryFeedback(
    val message: String,
    val kind: FeedbackKind = FeedbackKind.OTHER,
    val speciesId: String? = null,
    val language: String? = null,
) {
    init {
        require(message.isNotBlank()) { "Feedback cannot be blank" }
        require(message.length <= 2_000) { "Feedback is limited to 2,000 characters" }
    }
}

enum class FeedbackOutcome { SENT, RATE_LIMITED, FAILED, UNAVAILABLE }

fun interface FeedbackGateway {
    suspend fun submit(feedback: VoluntaryFeedback): FeedbackOutcome
}

class FeedbackCoordinator(
    private val gateway: FeedbackGateway,
) {
    suspend fun submit(feedback: VoluntaryFeedback): FeedbackOutcome = gateway.submit(feedback)
}

object DisabledFeedbackGateway : FeedbackGateway {
    override suspend fun submit(feedback: VoluntaryFeedback) = FeedbackOutcome.UNAVAILABLE
}

private const val REVIEW_DAY_MILLIS = 24L * 60L * 60L * 1_000L

data class ReviewHistory(
    val newSessionCount: Int,
    val pending: Boolean,
    val lastRequestedAtEpochMillis: Long?,
    val discoverySeen: Boolean,
)

/** Builds a history from stored values; the old "already requested" flag becomes a request date. */
fun storedReviewHistory(
    newSessionCount: Int,
    pending: Boolean,
    lastRequestedAtEpochMillis: Long?,
    discoverySeen: Boolean,
    legacyRequested: Boolean,
    nowEpochMillis: Long,
) = ReviewHistory(
    newSessionCount = newSessionCount,
    pending = pending,
    lastRequestedAtEpochMillis = if (legacyRequested && lastRequestedAtEpochMillis == null) {
        nowEpochMillis
    } else {
        lastRequestedAtEpochMillis
    },
    discoverySeen = discoverySeen,
)

/** Raw persisted review values, before the legacy "already requested" flag is folded into a date. */
data class RawReviewValues(
    val newSessionCount: Int,
    val pending: Boolean,
    val lastRequestedAtEpochMillis: Long?,
    val discoverySeen: Boolean,
    val legacyRequested: Boolean,
)

interface ReviewValuesStorage {
    fun read(): RawReviewValues
    fun write(history: ReviewHistory)
}

/** Loads through [storedReviewHistory] and persists a derived legacy request date at once, so it stays fixed. */
class MigratingReviewHistoryStore(
    private val storage: ReviewValuesStorage,
    private val currentTimeMillis: () -> Long,
) : ReviewHistoryStore {
    override fun load(): ReviewHistory {
        val raw = storage.read()
        val history = storedReviewHistory(
            newSessionCount = raw.newSessionCount,
            pending = raw.pending,
            lastRequestedAtEpochMillis = raw.lastRequestedAtEpochMillis,
            discoverySeen = raw.discoverySeen,
            legacyRequested = raw.legacyRequested,
            nowEpochMillis = currentTimeMillis(),
        )
        if (raw.legacyRequested) storage.write(history)
        return history
    }

    override fun save(updated: ReviewHistory) = storage.write(updated)
}

enum class ReviewPrompt { NONE, DISCOVERY, DIRECT }

class ReviewEligibilityPolicy(
    private val minimumNewSessions: Int = 5,
    private val cooldownMillis: Long = 90 * REVIEW_DAY_MILLIS,
) {
    init {
        require(minimumNewSessions > 0)
        require(cooldownMillis >= 0)
    }

    private fun cooledDown(history: ReviewHistory, nowEpochMillis: Long): Boolean =
        history.lastRequestedAtEpochMillis?.let { nowEpochMillis - it >= cooldownMillis } ?: true

    fun shouldMarkPending(history: ReviewHistory, calm: Boolean, nowEpochMillis: Long): Boolean =
        calm &&
            !history.pending &&
            history.newSessionCount >= minimumNewSessions &&
            cooledDown(history, nowEpochMillis)

    fun prompt(history: ReviewHistory, nowEpochMillis: Long): ReviewPrompt = when {
        !history.pending || !cooledDown(history, nowEpochMillis) -> ReviewPrompt.NONE
        !history.discoverySeen -> ReviewPrompt.DISCOVERY
        else -> ReviewPrompt.DIRECT
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
    fun onNewSessionSaved(calm: Boolean) {
        val now = currentTimeMillis()
        val previous = store.load()
        val counted = previous.copy(newSessionCount = previous.newSessionCount + 1)
        store.save(if (policy.shouldMarkPending(counted, calm, now)) counted.copy(pending = true) else counted)
    }

    fun pendingPrompt(): ReviewPrompt = policy.prompt(store.load(), currentTimeMillis())

    fun onDiscoveryAnswered(leaveReview: Boolean) {
        record(discoverySeen = true)
        if (leaveReview) gateway.requestReview()
    }

    fun requestDirect() {
        record(discoverySeen = store.load().discoverySeen)
        gateway.requestReview()
    }

    private fun record(discoverySeen: Boolean) {
        store.save(
            store.load().copy(
                pending = false,
                lastRequestedAtEpochMillis = currentTimeMillis(),
                discoverySeen = discoverySeen,
            ),
        )
    }
}
