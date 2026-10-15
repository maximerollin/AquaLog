package com.maximerollin.aqualog

import android.app.Application
import android.app.Activity
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.CrashReporter
import com.maximerollin.aqualog.shared.DisabledCrashReporter
import com.maximerollin.aqualog.shared.DisabledFeedbackGateway
import com.maximerollin.aqualog.shared.DisabledProductTelemetry
import com.maximerollin.aqualog.shared.FeedbackGateway
import com.maximerollin.aqualog.shared.ProductTelemetry
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.PortableDataService
import com.maximerollin.aqualog.shared.ReviewCoordinator
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.SyncCoordinator
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import java.util.UUID

open class AquaLogApplication : Application() {
    protected open val databaseName: String = AQUA_LOG_DATABASE_NAME

    private val database by lazy {
        createAquaLogDatabase(createAndroidDatabaseBuilder(this, databaseName))
    }

    open val aquariumRepository: AquariumRepository by lazy {
        RoomAquariumRepository(
            database = database,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
            onSyncNeeded = { AndroidSyncScheduler.requestImmediate(this) },
        )
    }

    protected open val secureTokenStorage: AndroidSecureTokenStorage by lazy {
        AndroidSecureTokenStorage(this)
    }

    private val accountSessionProvider by lazy {
        LocalAccountSyncSessionProvider(
            localData = aquariumRepository as RoomAquariumRepository,
            tokenStorage = secureTokenStorage,
            authGateway = SupabaseAuthGateway(
                this,
                SupabaseConfiguration.fromBuildConfig(),
                secureTokenStorage,
            ),
        )
    }

    open val syncCoordinator: SyncCoordinator by lazy {
        val localData = aquariumRepository as RoomAquariumRepository
        SyncCoordinator(
            localData = localData,
            backend = SupabaseSyncBackend(SupabaseConfiguration.fromBuildConfig()),
            sessionProvider = accountSessionProvider,
            currentTimeMillis = System::currentTimeMillis,
        )
    }

    open val portableDataService: PortableDataService by lazy {
        PortableDataService(database)
    }

    open val taskReminderScheduler: TaskReminderScheduler by lazy {
        AndroidTaskReminderScheduler(this)
    }

    /** External analytics/crash adapters stay disabled until a reviewed build-time integration is supplied. */
    open val productTelemetry: ProductTelemetry = DisabledProductTelemetry
    open val crashReporter: CrashReporter = DisabledCrashReporter
    open val feedbackGateway: FeedbackGateway = DisabledFeedbackGateway

    open val accountCoordinator: AccountActivationCoordinator
        get() {
            val localData = aquariumRepository as RoomAquariumRepository
            val configuration = SupabaseConfiguration.fromBuildConfig()
            return AccountActivationCoordinator(
                authGateway = SupabaseAuthGateway(this, configuration, secureTokenStorage),
                cloudRepository = SupabaseInitialMigrationRepository(configuration),
                localData = localData,
                tokenStorage = secureTokenStorage,
            )
        }

    private val googlePlayBillingGateway by lazy { GooglePlayBillingGateway(this) }
    private val defaultProPurchaseCoordinator by lazy {
        ProPurchaseCoordinator(
            billingGateway = googlePlayBillingGateway,
            purchaseVerifier = SupabaseProPurchaseVerifier(
                configuration = SupabaseConfiguration.fromBuildConfig(),
                sessionProvider = accountSessionProvider,
            ),
        )
    }

    open fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator {
        googlePlayBillingGateway.attach(activity)
        return defaultProPurchaseCoordinator
    }

    open fun reviewCoordinator(activity: Activity): ReviewCoordinator =
        androidReviewCoordinator(this, activity)
}
