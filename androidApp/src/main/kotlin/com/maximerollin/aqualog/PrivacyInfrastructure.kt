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
import com.maximerollin.aqualog.shared.ReviewHistoryStore
import com.maximerollin.aqualog.shared.storedReviewHistory
import java.lang.ref.WeakReference

internal class AndroidReviewHistoryStore(context: Context) : ReviewHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "aqualog-review-eligibility",
        Context.MODE_PRIVATE,
    )

    override fun load() = storedReviewHistory(
        newSessionCount = preferences.getInt(
            newSessionCountKey,
            preferences.getInt(legacySuccessCountKey, 0),
        ),
        pending = preferences.getBoolean(pendingKey, false),
        lastRequestedAtEpochMillis = preferences.getLong(lastRequestedKey, missingTimestamp)
            .takeUnless { it == missingTimestamp },
        discoverySeen = preferences.getBoolean(discoverySeenKey, false),
        legacyRequested = preferences.getBoolean(legacyRequestedKey, false),
        nowEpochMillis = System.currentTimeMillis(),
    )

    override fun save(updated: ReviewHistory) {
        preferences.edit()
            .putInt(newSessionCountKey, updated.newSessionCount)
            .putBoolean(pendingKey, updated.pending)
            .putLong(lastRequestedKey, updated.lastRequestedAtEpochMillis ?: missingTimestamp)
            .putBoolean(discoverySeenKey, updated.discoverySeen)
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

internal class GooglePlayInAppReviewGateway(activity: Activity, private val telemetry: ProductTelemetry = DisabledProductTelemetry) : InAppReviewGateway {
    private val activityReference = WeakReference(activity)

    override fun requestReview() {
        val activity = activityReference.get() ?: return
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
    activity: Activity,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
    telemetry: ProductTelemetry = DisabledProductTelemetry,
) = ReviewCoordinator(
    policy = ReviewEligibilityPolicy(),
    store = AndroidReviewHistoryStore(context),
    gateway = GooglePlayInAppReviewGateway(activity, telemetry),
    currentTimeMillis = currentTimeMillis,
)
