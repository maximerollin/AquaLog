package com.maximerollin.aqualog

import android.app.Application
import com.maximerollin.aqualog.shared.AQUA_LOG_DATABASE_NAME
import com.maximerollin.aqualog.shared.AccountActivationCoordinator
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import java.util.UUID

open class AquaLogApplication : Application() {
    protected open val databaseName: String = AQUA_LOG_DATABASE_NAME

    open val aquariumRepository: AquariumRepository by lazy {
        val database = createAquaLogDatabase(
            createAndroidDatabaseBuilder(this, databaseName),
        )
        RoomAquariumRepository(
            database = database,
            generateId = { UUID.randomUUID().toString() },
            currentTimeMillis = System::currentTimeMillis,
        )
    }

    open val accountCoordinator: AccountActivationCoordinator
        get() {
            val localData = aquariumRepository as RoomAquariumRepository
            val configuration = SupabaseConfiguration.fromBuildConfig()
            val tokenStorage = AndroidSecureTokenStorage(this)
            return AccountActivationCoordinator(
                authGateway = SupabaseAuthGateway(this, configuration, tokenStorage),
                cloudRepository = SupabaseInitialMigrationRepository(configuration, tokenStorage),
                localData = localData,
                tokenStorage = tokenStorage,
            )
        }
}
