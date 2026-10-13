package com.maximerollin.aqualog

import android.app.Application
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.PortableDataService
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
            onSyncNeeded = { AndroidSyncScheduler.enqueue(this) },
        )
    }

    protected open val secureTokenStorage: AndroidSecureTokenStorage by lazy {
        AndroidSecureTokenStorage(this)
    }

    open val syncCoordinator: SyncCoordinator by lazy {
        val localData = aquariumRepository as RoomAquariumRepository
        SyncCoordinator(
            localData = localData,
            backend = SupabaseSyncBackend(SupabaseConfiguration.fromBuildConfig()),
            sessionProvider = LocalAccountSyncSessionProvider(
                localData,
                secureTokenStorage,
                SupabaseAuthGateway(this, SupabaseConfiguration.fromBuildConfig(), secureTokenStorage),
            ),
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
            return AccountActivationCoordinator(
                authGateway = SupabaseAuthGateway(this, configuration, secureTokenStorage),
                cloudRepository = SupabaseInitialMigrationRepository(configuration),
                localData = localData,
                tokenStorage = secureTokenStorage,
            )
        }
}
