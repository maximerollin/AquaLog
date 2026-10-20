package com.maximerollin.aqualog

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.maximerollin.aqualog.shared.ANNUAL_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.AccountControlCoordinator
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AccountDeletionGateway
import com.maximerollin.aqualog.shared.AccountOperationGate
import com.maximerollin.aqualog.shared.AllowlistedAnalyticsAdapter
import com.maximerollin.aqualog.shared.AllowlistedProductTelemetry
import com.maximerollin.aqualog.shared.AnalyticsPayload
import com.maximerollin.aqualog.shared.AnnualProOffer
import com.maximerollin.aqualog.shared.AquaLogDatabase
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.LIFETIME_PRO_PRODUCT_ID
import com.maximerollin.aqualog.shared.LifetimeProOffer
import com.maximerollin.aqualog.shared.BillingLaunchResult
import com.maximerollin.aqualog.shared.CloudAccountDeletionResult
import com.maximerollin.aqualog.shared.FeedbackGateway
import com.maximerollin.aqualog.shared.PlayPurchaseUpdate
import com.maximerollin.aqualog.shared.PortableDataService
import com.maximerollin.aqualog.shared.ProBillingGateway
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.ProEntitlement
import com.maximerollin.aqualog.shared.ProPurchaseVerifier
import com.maximerollin.aqualog.shared.ProductTelemetry
import com.maximerollin.aqualog.shared.PurchaseAccountIdentityResult
import com.maximerollin.aqualog.shared.PurchaseVerificationResult
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.SecureTokenStorage
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.VoluntaryFeedback
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class AquaLogTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestAquaLogApplication::class.java.name, context)
}

class TestAquaLogApplication : AquaLogApplication() {
    var qualificationAccountCoordinator: AccountActivationCoordinator? = null
    override val accountCoordinator: AccountActivationCoordinator
        get() = qualificationAccountCoordinator ?: super.accountCoordinator
    private var database: AquaLogDatabase? = null
    private var repository: AquariumRepository? = null
    private var portableService: PortableDataService? = null
    private var accountControl: AccountControlCoordinator? = null
    private var roomRepository: RoomAquariumRepository? = null
    private var operationGate: AccountOperationGate? = null
    private var databaseSequence = 0
    val recordingTaskReminderScheduler = RecordingTaskReminderScheduler()
    val analyticsPayloads = CopyOnWriteArrayList<AnalyticsPayload>()
    val recordedFeedback = CopyOnWriteArrayList<VoluntaryFeedback>()

    override val productTelemetry: ProductTelemetry = AllowlistedProductTelemetry(
        adapter = AllowlistedAnalyticsAdapter(analyticsPayloads::add),
        enabled = true,
    )
    override val feedbackGateway: FeedbackGateway = FeedbackGateway(recordedFeedback::add)
    private var verifiedProEntitlement: ProEntitlement? = null

    override fun onCreate() {
        super.onCreate()
        resetRepository()
    }

    override val taskReminderScheduler: TaskReminderScheduler
        get() = recordingTaskReminderScheduler

    override val aquariumRepository: AquariumRepository
        get() = requireNotNull(repository) { "resetRepository() must run before launching MainActivity" }

    override fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator = ProPurchaseCoordinator(
        billingGateway = object : ProBillingGateway {
            override val purchaseUpdates: Flow<PlayPurchaseUpdate> = emptyFlow()
            override suspend fun loadAnnualOffer() = AnnualProOffer(
                productId = ANNUAL_PRO_PRODUCT_ID,
                formattedPrice = "€14.99",
                trialDays = 14,
                billingDateEpochMillis = 1_792_528_000_000L,
            )
            override suspend fun launchAnnualPurchase(obfuscatedAccountId: String) = BillingLaunchResult.Started
            override suspend fun restoreAnnualPurchase(): PlayPurchaseUpdate? = null
            override suspend fun loadLifetimeOffer() = LifetimeProOffer(
                productId = LIFETIME_PRO_PRODUCT_ID,
                formattedPrice = "€34.99",
            )
            override suspend fun launchLifetimePurchase(obfuscatedAccountId: String) = BillingLaunchResult.Started
            override suspend fun restoreLifetimePurchase(): PlayPurchaseUpdate? = null
        },
        purchaseVerifier = object : ProPurchaseVerifier {
            override suspend fun loadPurchaseAccountIdentity() =
                PurchaseAccountIdentityResult.Available("instrumented-test-account")
            override suspend fun loadEntitlement() =
                verifiedProEntitlement?.let(PurchaseVerificationResult::Verified)
                    ?: PurchaseVerificationResult.Rejected("No instrumented entitlement")
            override suspend fun verify(productId: String, purchaseToken: String) =
                PurchaseVerificationResult.Rejected("Test purchases require an explicit test")
        },
    )

    override val portableDataService: PortableDataService
        get() = requireNotNull(portableService) { "resetRepository() must run before launching MainActivity" }

    override val accountControlCoordinator: AccountControlCoordinator
        get() = requireNotNull(accountControl) { "resetRepository() must run before launching MainActivity" }

    override val accountOperationGate: AccountOperationGate
        get() = requireNotNull(operationGate) { "resetRepository() must run before launching MainActivity" }

    var deletionResult: CloudAccountDeletionResult = CloudAccountDeletionResult.Completed

    suspend fun linkAccount(accountId: String = "account-1", email: String = "owner@example.com") {
        requireNotNull(roomRepository).beginAccountMigration(accountId, email)
        requireNotNull(roomRepository).completeAccountMigration(accountId)
    }

    suspend fun beginPendingDeletion(operationKey: String = "deletion-1") {
        requireNotNull(roomRepository).beginDeletion(operationKey)
    }

    fun resetRepository() {
        qualificationAccountCoordinator = null
        database?.close()
        val databaseName = "aqualog-test-${databaseSequence++}.db"
        deleteDatabase(databaseName)
        val newDatabase = createAquaLogDatabase(createAndroidDatabaseBuilder(this, databaseName))
        database = newDatabase
        val newRepository = RoomAquariumRepository(
            database = newDatabase,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
        )
        roomRepository = newRepository
        repository = newRepository
        portableService = PortableDataService(newDatabase)
        val tokenStorage = TestTokenStorage(AuthTokens("access", "refresh"))
        operationGate = AccountOperationGate(newRepository)
        accountControl = AccountControlCoordinator(
            localData = newRepository,
            tokenStorage = tokenStorage,
            deletionGateway = object : AccountDeletionGateway {
                override suspend fun deleteAccount(
                    accessToken: String,
                    operationKey: String,
                    recoveryCredential: String?,
                ) = deletionResult
            },
            operationGate = accountOperationGate,
            generateOperationKey = { "test-deletion-operation" },
        )
        deletionResult = CloudAccountDeletionResult.Completed
        recordingTaskReminderScheduler.reset()
        analyticsPayloads.clear()
        recordedFeedback.clear()
        verifiedProEntitlement = null
    }

    fun grantPro(entitlement: ProEntitlement) {
        verifiedProEntitlement = entitlement
    }

    fun revokePro() {
        verifiedProEntitlement = null
    }

    fun installRepository(value: AquariumRepository) {
        database?.close()
        database = null
        repository = value
        roomRepository = value as? RoomAquariumRepository
        recordingTaskReminderScheduler.reset()
    }

    override fun onTerminate() {
        database?.close()
        super.onTerminate()
    }
}

private class TestTokenStorage(private var tokens: AuthTokens?) : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun read() = tokens
    override suspend fun clear() { tokens = null }
}

class RecordingTaskReminderScheduler : TaskReminderScheduler {
    val scheduledOccurrenceIds = CopyOnWriteArrayList<String>()
    val canceledOccurrenceIds = CopyOnWriteArrayList<String>()

    override fun schedule(occurrence: TaskOccurrence) {
        scheduledOccurrenceIds += occurrence.id
    }

    override fun cancel(occurrenceId: String) {
        canceledOccurrenceIds += occurrenceId
    }

    override fun reconcile(occurrences: List<TaskOccurrence>) {
        occurrences.forEach(::schedule)
    }

    override fun isBatteryOptimizationActive(): Boolean = false

    fun reset() {
        scheduledOccurrenceIds.clear()
        canceledOccurrenceIds.clear()
    }
}
