package com.maximerollin.aqualog

import android.app.Activity
import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.maximerollin.aqualog.shared.ProductEvent
import com.maximerollin.aqualog.shared.ProductTelemetry
import com.maximerollin.aqualog.shared.DisabledProductTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.play.core.review.ReviewManagerFactory
import com.maximerollin.aqualog.shared.InAppReviewGateway
import com.maximerollin.aqualog.shared.ReviewCoordinator
import com.maximerollin.aqualog.shared.ReviewEligibilityPolicy
import com.maximerollin.aqualog.shared.ReviewHistory
import com.maximerollin.aqualog.shared.MigratingReviewHistoryStore
import com.maximerollin.aqualog.shared.RawReviewValues
import com.maximerollin.aqualog.shared.ReviewValuesStorage

internal class AndroidReviewValuesStorage(context: Context) : ReviewValuesStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "aqualog-review-eligibility",
        Context.MODE_PRIVATE,
    )

    override fun read() = RawReviewValues(
        newSessionCount = preferences.getInt(
            newSessionCountKey,
            preferences.getInt(legacySuccessCountKey, 0),
        ),
        pending = preferences.getBoolean(pendingKey, false),
        lastRequestedAtEpochMillis = preferences.getLong(lastRequestedKey, missingTimestamp)
            .takeUnless { it == missingTimestamp },
        discoverySeen = preferences.getBoolean(discoverySeenKey, false),
        legacyRequested = preferences.getBoolean(legacyRequestedKey, false),
    )

    override fun write(history: ReviewHistory) {
        preferences.edit()
            .putInt(newSessionCountKey, history.newSessionCount)
            .putBoolean(pendingKey, history.pending)
            .putLong(lastRequestedKey, history.lastRequestedAtEpochMillis ?: missingTimestamp)
            .putBoolean(discoverySeenKey, history.discoverySeen)
            // The legacy flag is now carried by the request date; drop it once that date is stored.
            .remove(legacyRequestedKey)
            .apply()
    }

    private companion object {
        const val newSessionCountKey = "new-session-count"
        const val legacySuccessCountKey = "successful-session-count"
        const val pendingKey = "review-pending"
        const val lastRequestedKey = "review-last-requested-at"
        const val discoverySeenKey = "review-discovery-seen"
        const val legacyRequestedKey = "review-requested"
        const val missingTimestamp = Long.MIN_VALUE
    }
}

/** Resolves the Activity at request time: the coordinator outlives the Activity across rotations and theme changes. */
internal class GooglePlayInAppReviewGateway(
    private val currentActivity: () -> Activity?,
    private val telemetry: ProductTelemetry = DisabledProductTelemetry,
) : InAppReviewGateway {
    override fun requestReview() {
        val activity = currentActivity() ?: return
        val manager = ReviewManagerFactory.create(activity.applicationContext)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
                manager.launchReviewFlow(activity, request.result)
                runCatching { telemetry.record(ProductEvent.ReviewRequested) }
                (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
                    runCatching { notifyLogSnagReview() }
                }
            }
        }
    }
}

internal fun androidReviewCoordinator(
    context: Context,
    currentActivity: () -> Activity?,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
    telemetry: ProductTelemetry = DisabledProductTelemetry,
): ReviewCoordinator {
    val store = MigratingReviewHistoryStore(AndroidReviewValuesStorage(context), currentTimeMillis)
    // Loading now fixes a legacy request date at app start after the update, not at the first new Session.
    store.load()
    return ReviewCoordinator(
        policy = ReviewEligibilityPolicy(),
        store = store,
        gateway = GooglePlayInAppReviewGateway(currentActivity, telemetry),
        currentTimeMillis = currentTimeMillis,
    )
}
