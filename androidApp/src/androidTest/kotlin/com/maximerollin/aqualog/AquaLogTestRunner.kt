package com.maximerollin.aqualog

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.maximerollin.aqualog.shared.AquaLogDatabase
import com.maximerollin.aqualog.shared.AquariumRepository
import com.maximerollin.aqualog.shared.RoomAquariumRepository
import com.maximerollin.aqualog.shared.PortableDataService
import com.maximerollin.aqualog.shared.createAndroidDatabaseBuilder
import com.maximerollin.aqualog.shared.createAquaLogDatabase
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import com.maximerollin.aqualog.shared.TaskOccurrence
import com.maximerollin.aqualog.shared.AccountControlCoordinator
import com.maximerollin.aqualog.shared.AccountDeletionGateway
import com.maximerollin.aqualog.shared.AuthTokens
import com.maximerollin.aqualog.shared.CloudAccountDeletionResult
import com.maximerollin.aqualog.shared.SecureTokenStorage

class AquaLogTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestAquaLogApplication::class.java.name, context)
}

class TestAquaLogApplication : AquaLogApplication() {
    private var database: AquaLogDatabase? = null
    private var repository: AquariumRepository? = null
    private var portableService: PortableDataService? = null
    private var accountControl: AccountControlCoordinator? = null
    private var roomRepository: RoomAquariumRepository? = null
    private var databaseSequence = 0
    val recordingTaskReminderScheduler = RecordingTaskReminderScheduler()

    override val taskReminderScheduler: TaskReminderScheduler
        get() = recordingTaskReminderScheduler

    override val aquariumRepository: AquariumRepository
        get() = requireNotNull(repository) { "resetRepository() must run before launching MainActivity" }

    override val portableDataService: PortableDataService
        get() = requireNotNull(portableService) { "resetRepository() must run before launching MainActivity" }

    override val accountControlCoordinator: AccountControlCoordinator
        get() = requireNotNull(accountControl) { "resetRepository() must run before launching MainActivity" }

    var deletionResult: CloudAccountDeletionResult = CloudAccountDeletionResult.Completed

    suspend fun linkAccount(accountId: String = "account-1", email: String = "owner@example.com") {
        requireNotNull(roomRepository).beginAccountMigration(accountId, email)
        requireNotNull(roomRepository).completeAccountMigration(accountId)
    }

    suspend fun beginPendingDeletion(operationKey: String = "deletion-1") {
        requireNotNull(roomRepository).beginDeletion(operationKey)
    }

    fun resetRepository() {
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
        accountControl = AccountControlCoordinator(
            localData = newRepository,
            tokenStorage = tokenStorage,
            deletionGateway = object : AccountDeletionGateway {
                override suspend fun deleteAccount(accessToken: String, operationKey: String) = deletionResult
            },
            generateOperationKey = { "test-deletion-operation" },
        )
        deletionResult = CloudAccountDeletionResult.Completed
        recordingTaskReminderScheduler.reset()
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
