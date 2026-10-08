package com.maximerollin.aqualog.shared

import kotlinx.coroutines.CancellationException

enum class SyncEntityType(val storageValue: String) {
    AQUARIUM("aquarium"),
    PARAMETER_DEFINITION("parameter_definition"),
    SESSION("session"),
    MEASUREMENT("measurement"),
    MAINTENANCE_ACTION("maintenance_action"),
    EVENT("event");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

data class SyncOperation(
    val id: Long,
    val entityType: SyncEntityType,
    val entityId: String,
    val attemptCount: Int,
)

enum class SyncStatus {
    SYNCED,
    PENDING,
}

data class SyncState(
    val status: SyncStatus,
    val pendingOperationCount: Int,
)

data class PendingSyncBatch(
    val operations: List<SyncOperation>,
    val aquariums: List<Aquarium>,
    val parameterDefinitions: List<ParameterDefinition>,
    val sessions: List<Session>,
    val measurements: List<Measurement>,
    val maintenanceActions: List<MaintenanceAction>,
    val events: List<SessionEvent>,
)

interface SyncLocalDataSource {
    fun observeSyncState(): kotlinx.coroutines.flow.Flow<SyncState>
    suspend fun pendingSyncBatch(limit: Int = 100): PendingSyncBatch
    suspend fun acknowledgeSyncOperations(operationIds: List<Long>)
    suspend fun recordSyncFailure(operationIds: List<Long>)
    suspend fun applyRemoteCopy(copy: InitialAccountCopy)
}

data class SyncSession(
    val accountId: String,
    val accessToken: String,
)

interface SyncSessionProvider {
    suspend fun currentSession(): SyncSession?
}

interface SyncBackend {
    suspend fun push(session: SyncSession, batch: PendingSyncBatch)
    suspend fun pull(session: SyncSession): InitialAccountCopy
}

sealed interface SyncRunResult {
    data class Synchronized(val downloadedSessionCount: Int) : SyncRunResult
    data class Retry(val afterMillis: Long) : SyncRunResult
    data object SignedOut : SyncRunResult
}

class SyncRetryPolicy(
    private val baseDelayMillis: Long = 1_000L,
    private val maximumDelayMillis: Long = 60_000L,
) {
    fun delayMillis(attempt: Int): Long {
        val multiplier = 1L shl (attempt - 1).coerceIn(0, 20)
        return (baseDelayMillis * multiplier).coerceAtMost(maximumDelayMillis)
    }
}

class SyncCoordinator(
    private val localData: SyncLocalDataSource,
    private val backend: SyncBackend,
    private val sessionProvider: SyncSessionProvider,
    private val retryPolicy: SyncRetryPolicy = SyncRetryPolicy(),
) {
    suspend fun runOnce(): SyncRunResult {
        val session = sessionProvider.currentSession() ?: return SyncRunResult.SignedOut
        val batch = localData.pendingSyncBatch()
        return try {
            if (batch.operations.isNotEmpty()) backend.push(session, batch)
            val remoteCopy = backend.pull(session)
            localData.applyRemoteCopy(remoteCopy)
            localData.acknowledgeSyncOperations(batch.operations.map(SyncOperation::id))
            SyncRunResult.Synchronized(remoteCopy.sessions.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val operationIds = batch.operations.map(SyncOperation::id)
            localData.recordSyncFailure(operationIds)
            val attempt = (batch.operations.maxOfOrNull(SyncOperation::attemptCount) ?: 0) + 1
            SyncRunResult.Retry(retryPolicy.delayMillis(attempt))
        }
    }
}
