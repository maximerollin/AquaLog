package com.maximerollin.aqualog

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.maximerollin.aqualog.shared.AllowlistedAnalyticsAdapter
import com.maximerollin.aqualog.shared.AllowlistedCrashAdapter
import com.maximerollin.aqualog.shared.AllowlistedCrashReporter
import com.maximerollin.aqualog.shared.AllowlistedProductTelemetry
import com.maximerollin.aqualog.shared.AnalyticsPayload
import com.maximerollin.aqualog.shared.AnalyticsPayloadSink
import com.maximerollin.aqualog.shared.AnalyticsValue
import com.maximerollin.aqualog.shared.CrashReporter
import com.maximerollin.aqualog.shared.DisabledCrashReporter
import com.maximerollin.aqualog.shared.DisabledFeedbackGateway
import com.maximerollin.aqualog.shared.DisabledProductTelemetry
import com.maximerollin.aqualog.shared.FeedbackGateway
import com.maximerollin.aqualog.shared.FeedbackOutcome
import com.maximerollin.aqualog.shared.ProductAnalyticsSchema
import com.maximerollin.aqualog.shared.ProductTelemetry
import com.maximerollin.aqualog.shared.RawTechnicalFailure
import com.maximerollin.aqualog.shared.SyncSessionProvider
import com.maximerollin.aqualog.shared.TechnicalFailureSchema
import com.maximerollin.aqualog.shared.TechnicalFailureSink
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import com.posthog.PostHog
import com.posthog.PersonProfiles
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ObservabilityConfiguration(
    val postHogApiKey: String,
    val postHogHost: String,
    val firebaseApplicationId: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String,
    val feedbackUrl: String,
) {
    val hasPostHog: Boolean get() = postHogApiKey.isNotBlank() && postHogHost.isSafeEndpoint()
    val hasFirebase: Boolean
        get() = firebaseApplicationId.isNotBlank() && firebaseApiKey.isNotBlank() && firebaseProjectId.isNotBlank()
    val hasFeedback: Boolean get() = feedbackUrl.isSafeEndpoint()

    companion object {
        fun fromBuildConfig(
            supabaseUrl: String = BuildConfig.SUPABASE_URL,
            supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
        ) = ObservabilityConfiguration(
            postHogApiKey = BuildConfig.POSTHOG_API_KEY,
            postHogHost = BuildConfig.POSTHOG_HOST.ifBlank { "https://us.i.posthog.com" },
            firebaseApplicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            firebaseApiKey = BuildConfig.FIREBASE_API_KEY,
            firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
            feedbackUrl = BuildConfig.FEEDBACK_URL.ifBlank {
                supabaseUrl.trimEnd('/')
                    .takeIf { it.isNotBlank() && supabaseAnonKey.isNotBlank() }
                    ?.plus("/functions/v1/submit-feedback")
                    .orEmpty()
            },
        )
    }
}

internal fun createProductTelemetry(context: Context, config: ObservabilityConfiguration): ProductTelemetry {
    if (!config.hasPostHog || BuildConfig.DEBUG || isFirebaseTestLab(context)) return DisabledProductTelemetry
    val postHogConfig = PostHogAndroidConfig(config.postHogApiKey, config.postHogHost).apply {
        captureApplicationLifecycleEvents = false
        captureDeepLinks = false
        captureScreenViews = false
        capturePushNotificationOpened = false
        capturePushNotificationSubscriptions = false
        sessionReplay = false
        preloadFeatureFlags = false
        sendFeatureFlagEvent = false
        setDefaultPersonProperties = false
        personProfiles = PersonProfiles.NEVER
        surveys = false
        errorTrackingConfig.autoCapture = false
    }
    PostHogAndroid.setup(context.applicationContext, postHogConfig)
    return AllowlistedProductTelemetry(
        AllowlistedAnalyticsAdapter(PostHogAnalyticsSink(releaseChannel = BuildConfig.DISTRIBUTION_TRACK)),
        enabled = true,
    )
}

internal fun interface PostHogCaptureClient {
    fun capture(name: String, properties: Map<String, Any>)
}

internal class PostHogAnalyticsSink(
    private val releaseChannel: String? = null,
    private val client: PostHogCaptureClient = PostHogCaptureClient { name, properties ->
        PostHog.capture(event = name, properties = properties)
    },
) : AnalyticsPayloadSink {
    override fun send(payload: AnalyticsPayload) {
        require(ProductAnalyticsSchema.isAllowed(payload))
        val tagged = if (releaseChannel == null) payload else payload.copy(
            properties = payload.properties + ("release_channel" to AnalyticsValue.Category(releaseChannel)),
        )
        require(ProductAnalyticsSchema.isAllowed(tagged))
        client.capture(
            tagged.name,
            tagged.properties.mapValues { (_, value) ->
                when (value) {
                    is AnalyticsValue.Flag -> value.value
                    is AnalyticsValue.Category -> value.value
                }
            },
        )
    }
}

internal fun createCrashReporter(context: Context, config: ObservabilityConfiguration): CrashReporter {
    if (BuildConfig.DEBUG || isFirebaseTestLab(context)) return DisabledCrashReporter
    return createCrashReporter(config) {
        if (runCatching { FirebaseApp.getInstance() }.getOrNull() == null) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(config.firebaseApplicationId)
                    .setApiKey(config.firebaseApiKey)
                    .setProjectId(config.firebaseProjectId)
                    .build(),
            )
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        FirebaseTechnicalFailureClient { failure ->
                val keys = CustomKeysAndValues.Builder()
                    .putString("failure_kind", failure.kind)
                    .putString("component", requireNotNull(failure.context["component"]))
                failure.context["sync_category"]?.let { keys.putString("sync_category", it) }
                crashlytics.recordException(AquaLogTechnicalFailure(failure.kind), keys.build())
        }
    }
}

internal fun createCrashReporter(
    config: ObservabilityConfiguration,
    clientFactory: () -> FirebaseTechnicalFailureClient,
): CrashReporter {
    if (!config.hasFirebase) return DisabledCrashReporter
    val client = runCatching(clientFactory).getOrNull() ?: return DisabledCrashReporter
    return AllowlistedCrashReporter(
        AllowlistedCrashAdapter(FirebaseTechnicalFailureSink(client)),
        enabled = true,
    )
}

internal fun interface FirebaseTechnicalFailureClient {
    fun record(failure: RawTechnicalFailure)
}

internal class FirebaseTechnicalFailureSink(
    private val client: FirebaseTechnicalFailureClient,
) : TechnicalFailureSink {
    override fun record(failure: RawTechnicalFailure) {
        require(TechnicalFailureSchema.isAllowed(failure))
        client.record(failure)
    }
}

private class AquaLogTechnicalFailure(kind: String) : RuntimeException("AquaLog technical failure: $kind")

internal fun createFeedbackGateway(
    config: ObservabilityConfiguration,
    sessionProvider: SyncSessionProvider,
    installIds: FeedbackInstallIdStore,
): FeedbackGateway = if (config.hasFeedback) {
    SupabaseFeedbackGateway(config.feedbackUrl, sessionProvider, installIds, BuildConfig.VERSION_NAME)
} else {
    DisabledFeedbackGateway
}

/** Sends feedback with the account when there is one, anonymously (install id) otherwise. */
internal class SupabaseFeedbackGateway(
    private val endpoint: String,
    private val sessionProvider: SyncSessionProvider,
    private val installIds: FeedbackInstallIdStore,
    private val appVersion: String,
    private val transport: FeedbackTransport = HttpFeedbackTransport,
) : FeedbackGateway {
    override suspend fun submit(feedback: VoluntaryFeedback): FeedbackOutcome {
        val session = sessionProvider.currentSession()
        if (session == null) return anonymous(feedback)

        val first = post(feedback, session.accessToken)
        if (first != HTTP_UNAUTHORIZED) return outcome(first)

        // The token was rejected: refresh once, then fall back to anonymous rather than lose the feedback.
        val refreshed = sessionProvider.refreshSession(session) ?: return anonymous(feedback)
        val second = post(feedback, refreshed.accessToken)
        return if (second == HTTP_UNAUTHORIZED) anonymous(feedback) else outcome(second)
    }

    private suspend fun anonymous(feedback: VoluntaryFeedback): FeedbackOutcome =
        outcome(post(feedback, accessToken = null))

    private suspend fun post(feedback: VoluntaryFeedback, accessToken: String?): Int {
        val body = JSONObject()
            .put("message", feedback.message)
            .put("kind", feedback.kind.wireValue)
            .put("appVersion", appVersion)
        feedback.speciesId?.let { body.put("speciesId", it) }
        feedback.language?.let { body.put("language", it) }
        if (accessToken == null) body.put("installId", installIds.getOrCreate())
        return transport.post(endpoint, accessToken, body)
    }

    private fun outcome(code: Int): FeedbackOutcome = when (code) {
        in 200..299 -> FeedbackOutcome.SENT
        HTTP_TOO_MANY_REQUESTS -> FeedbackOutcome.RATE_LIMITED
        else -> FeedbackOutcome.FAILED
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

internal fun interface FeedbackTransport {
    /** Returns the HTTP status code, or -1 on an IO error. A null token sends no Authorization header. */
    suspend fun post(endpoint: String, accessToken: String?, body: JSONObject): Int
}

private object HttpFeedbackTransport : FeedbackTransport {
    override suspend fun post(endpoint: String, accessToken: String?, body: JSONObject): Int = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                if (accessToken != null) setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { output -> output.write(body.toString().toByteArray()) }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        } catch (_: java.io.IOException) {
            -1
        }
    }
}

private fun String.isSafeEndpoint(): Boolean = runCatching {
    val parsed = URL(this)
    parsed.protocol == "https" ||
        (parsed.protocol == "http" && parsed.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
}.getOrDefault(false)

internal fun isFirebaseTestLab(context: Context): Boolean =
    android.provider.Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
