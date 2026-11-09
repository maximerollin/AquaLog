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
import com.maximerollin.aqualog.shared.ProEntitlementCache
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.PortableDataService
import com.maximerollin.aqualog.shared.ReviewCoordinator
import com.maximerollin.aqualog.shared.DirectoryAquariumPhotoStore
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.SyncCoordinator
import com.maximerollin.aqualog.shared.TechnicalComponent
import com.maximerollin.aqualog.shared.TechnicalFailure
import com.maximerollin.aqualog.shared.TechnicalFailureKind
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import com.maximerollin.aqualog.widget.updateAllWidgets
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

open class AquaLogApplication : Application() {
    // A background failure (widget refresh, data observation) is reported, never fatal.
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ ->
            runCatching { crashReporter.record(TechnicalFailure(TechnicalFailureKind.NON_FATAL, TechnicalComponent.DATABASE)) }
        },
    )

    /** Widgets follow the data while the process lives; otherwise they refresh hourly (updatePeriodMillis). */
    @OptIn(FlowPreview::class)
    protected open fun keepWidgetsFresh() {
        applicationScope.launch {
            aquariumRepository.observeChanges().debounce(1_000).collect { updateAllWidgets(this@AquaLogApplication) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        keepWidgetsFresh()
    }

    protected open val databaseName: String = AQUA_LOG_DATABASE_NAME

    private val database by lazy {
        createAquaLogDatabase(createAndroidDatabaseBuilder(this, databaseName))
    }

    protected val aquariumPhotoStore by lazy { DirectoryAquariumPhotoStore(File(filesDir, "aquarium-photos")) }

    open val aquariumRepository: AquariumRepository by lazy {
        RoomAquariumRepository(
            database = database,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
            onSyncNeeded = { AndroidSyncScheduler.requestImmediate(this) },
            photoStore = aquariumPhotoStore,
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
            photoStore = aquariumPhotoStore,
        )
    }

    open val portableDataService: PortableDataService by lazy {
        PortableDataService(database, aquariumPhotoStore)
    }

    open val taskReminderScheduler: TaskReminderScheduler by lazy {
        AndroidTaskReminderScheduler(this)
    }

    /** The home's clock; instrumented tests replace it. */
    open val currentTimeMillis: () -> Long = System::currentTimeMillis
    open val currentTimeZoneId: () -> String = { ZoneId.systemDefault().id }

    private val observabilityConfiguration by lazy { ObservabilityConfiguration.fromBuildConfig() }

    /** Each adapter stays disabled unless its complete build/environment configuration is present. */
    open val productTelemetry: ProductTelemetry by lazy {
        createProductTelemetry(this, observabilityConfiguration)
    }
    open val crashReporter: CrashReporter by lazy {
        createCrashReporter(this, observabilityConfiguration)
    }
    open val feedbackGateway: FeedbackGateway by lazy {
        createFeedbackGateway(observabilityConfiguration, accountSessionProvider, feedbackInstallIds)
    }
    internal open val feedbackInstallIds: FeedbackInstallIdStore by lazy { AndroidFeedbackInstallIdStore(this) }

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
            onAccountDeleted = { feedbackInstallIds.clear() },
        )
    }

    /** Pro as last answered by the server; the widget reads it without a network. */
    val proEntitlementCache: ProEntitlementCache by lazy {
        SharedPreferencesProEntitlementCache(this, onChanged = { applicationScope.launch { updateAllWidgets(this@AquaLogApplication) } })
    }

    private val revenueCatBillingGateway by lazy { RevenueCatBillingGateway(this) }
    private val defaultProPurchaseCoordinator by lazy {
        ProPurchaseCoordinator(
            billingGateway = revenueCatBillingGateway,
            purchaseVerifier = SupabaseProPurchaseVerifier(
                configuration = SupabaseConfiguration.fromBuildConfig(),
                sessionProvider = accountSessionProvider,
            ),
            entitlementCache = proEntitlementCache,
        )
    }

    open fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator {
        revenueCatBillingGateway.attach(activity)
        return defaultProPurchaseCoordinator
    }

    open fun reviewCoordinator(activity: Activity): ReviewCoordinator =
        androidReviewCoordinator(this, activity, telemetry = productTelemetry)
}
