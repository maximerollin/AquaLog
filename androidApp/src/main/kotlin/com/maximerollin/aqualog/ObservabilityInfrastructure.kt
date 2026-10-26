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
): FeedbackGateway = if (config.hasFeedback) {
    AuthenticatedFeedbackGateway(config.feedbackUrl, sessionProvider)
} else {
    DisabledFeedbackGateway
}

internal class AuthenticatedFeedbackGateway(
    private val endpoint: String,
    private val sessionProvider: SyncSessionProvider,
    private val transport: FeedbackTransport = HttpFeedbackTransport,
) : FeedbackGateway {
    override suspend fun submit(feedback: VoluntaryFeedback): Boolean {
        val session = sessionProvider.currentSession() ?: return false
        return transport.post(endpoint, session.accessToken, feedback.message)
    }
}

internal fun interface FeedbackTransport {
    suspend fun post(endpoint: String, accessToken: String, message: String): Boolean
}

private object HttpFeedbackTransport : FeedbackTransport {
    override suspend fun post(endpoint: String, accessToken: String, message: String): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { output ->
                output.write(JSONObject(mapOf("message" to message)).toString().toByteArray())
            }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
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
