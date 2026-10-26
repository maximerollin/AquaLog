package com.maximerollin.aqualog

import android.app.Application
import android.app.Activity
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AccountControlCoordinator
import com.maximerollin.aqualog.shared.AccountOperationGate
import com.maximerollin.aqualog.shared.DeletionAwareSyncSessionProvider
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.CrashReporter
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

    open val accountOperationGate: AccountOperationGate by lazy {
        AccountOperationGate(aquariumRepository as RoomAquariumRepository)
    }

    private val accountSessionProvider by lazy {
        val localData = aquariumRepository as RoomAquariumRepository
        DeletionAwareSyncSessionProvider(
            localData,
            LocalAccountSyncSessionProvider(
                localData = localData,
                tokenStorage = secureTokenStorage,
                authGateway = SupabaseAuthGateway(
                    this,
                    SupabaseConfiguration.fromBuildConfig(),
                    secureTokenStorage,
                ),
                operationGate = accountOperationGate,
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
            operationGate = accountOperationGate,
        )
    }

    open val portableDataService: PortableDataService by lazy {
        PortableDataService(database)
    }

    open val taskReminderScheduler: TaskReminderScheduler by lazy {
        AndroidTaskReminderScheduler(this)
    }

    private val observabilityConfiguration by lazy { ObservabilityConfiguration.fromBuildConfig() }

    /** Each adapter stays disabled unless its complete build/environment configuration is present. */
    open val productTelemetry: ProductTelemetry by lazy {
        createProductTelemetry(this, observabilityConfiguration)
    }
    open val crashReporter: CrashReporter by lazy {
        createCrashReporter(this, observabilityConfiguration)
    }
    open val feedbackGateway: FeedbackGateway by lazy {
        createFeedbackGateway(observabilityConfiguration, accountSessionProvider)
    }

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

    open val accountControlCoordinator: AccountControlCoordinator by lazy {
        val configuration = SupabaseConfiguration.fromBuildConfig()
        AccountControlCoordinator(
            localData = aquariumRepository as RoomAquariumRepository,
            tokenStorage = secureTokenStorage,
            deletionGateway = SupabaseAccountDeletionGateway(configuration),
            operationGate = accountOperationGate,
            deletionCredentialStorage = secureTokenStorage,
            reauthenticationGateway = SupabaseAuthGateway(this, configuration, secureTokenStorage),
            generateOperationKey = { UUID.randomUUID().toString() },
        )
    }

    private val revenueCatBillingGateway by lazy { RevenueCatBillingGateway(this) }
    private val defaultProPurchaseCoordinator by lazy {
        ProPurchaseCoordinator(
            billingGateway = revenueCatBillingGateway,
            purchaseVerifier = SupabaseProPurchaseVerifier(
                configuration = SupabaseConfiguration.fromBuildConfig(),
                sessionProvider = accountSessionProvider,
            ),
        )
    }

    open fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator {
        revenueCatBillingGateway.attach(activity)
        return defaultProPurchaseCoordinator
    }

    open fun reviewCoordinator(activity: Activity): ReviewCoordinator =
        androidReviewCoordinator(this, activity)
}
