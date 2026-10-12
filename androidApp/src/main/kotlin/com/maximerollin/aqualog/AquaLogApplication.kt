package com.maximerollin.aqualog

import android.app.Application
import android.app.Activity
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.ProPurchaseCoordinator
import com.maximerollin.aqualog.shared.PortableDataService
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
        )
    }

    open val portableDataService: PortableDataService by lazy {
        PortableDataService(database)
    }

    open val taskReminderScheduler: TaskReminderScheduler by lazy {
        AndroidTaskReminderScheduler(this)
    }

    open val accountCoordinator: AccountActivationCoordinator
        get() {
            val localData = aquariumRepository as RoomAquariumRepository
            val configuration = SupabaseConfiguration.fromBuildConfig()
            val tokenStorage = AndroidSecureTokenStorage(this)
            return AccountActivationCoordinator(
                authGateway = SupabaseAuthGateway(this, configuration, tokenStorage),
                cloudRepository = SupabaseInitialMigrationRepository(configuration),
                localData = localData,
                tokenStorage = tokenStorage,
            )
        }

    private val googlePlayBillingGateway by lazy { GooglePlayBillingGateway(this) }
    private val defaultProPurchaseCoordinator by lazy {
        val tokenStorage = AndroidSecureTokenStorage(this)
        ProPurchaseCoordinator(
            billingGateway = googlePlayBillingGateway,
            purchaseVerifier = SupabaseProPurchaseVerifier(
                configuration = SupabaseConfiguration.fromBuildConfig(),
                tokenStorage = tokenStorage,
            ),
        )
    }

    open fun proPurchaseCoordinator(activity: Activity): ProPurchaseCoordinator {
        googlePlayBillingGateway.attach(activity)
        return defaultProPurchaseCoordinator
    }
}
