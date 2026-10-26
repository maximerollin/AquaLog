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
import java.lang.ref.WeakReference

internal class AndroidReviewHistoryStore(context: Context) : ReviewHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "aqualog-review-eligibility",
        Context.MODE_PRIVATE,
    )

    override fun load() = ReviewHistory(
        successfulSessionCount = preferences.getInt(successCountKey, 0),
        firstSuccessfulSessionAtEpochMillis = preferences.getLong(firstSuccessKey, missingTimestamp)
            .takeUnless { it == missingTimestamp },
        requested = preferences.getBoolean(requestedKey, false),
    )

    override fun save(updated: ReviewHistory) {
        preferences.edit()
            .putInt(successCountKey, updated.successfulSessionCount)
            .putLong(firstSuccessKey, updated.firstSuccessfulSessionAtEpochMillis ?: missingTimestamp)
            .putBoolean(requestedKey, updated.requested)
            .apply()
    }

    private companion object {
        const val successCountKey = "successful-session-count"
        const val firstSuccessKey = "first-success-at"
        const val requestedKey = "review-requested"
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
