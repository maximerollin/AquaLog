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
    val operationKey: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val kind: SyncOperationKind,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val clientUpdatedAtEpochMillis: Long,
    val baseServerVersion: Long,
)

enum class SyncOperationKind(val storageValue: String) {
    UPSERT("upsert"),
    DELETE("delete");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

enum class SyncStatus {
    SYNCED,
    PENDING,
    FAILED,
}

data class SyncState(
    val status: SyncStatus,
    val pendingOperationCount: Int,
    val failure: SyncFailure? = null,
    val nextRetryAtEpochMillis: Long? = null,
)

enum class SyncFailureCategory {
    NETWORK,
    CONFLICT,
    SERVER,
    UNKNOWN,
}

data class SyncFailure(
    val category: SyncFailureCategory,
    val technicalDetail: String?,
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

data class RemoteEntityVersion(
    val entityType: SyncEntityType,
    val entityId: String,
    val serverVersion: Long,
    val serverUpdatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)

data class RemoteSyncSnapshot(
    val copy: InitialAccountCopy,
    val versions: List<RemoteEntityVersion>,
)

interface SyncLocalDataSource {
    fun observeSyncState(): kotlinx.coroutines.flow.Flow<SyncState>
    suspend fun pendingSyncBatch(limit: Int = 100, eligibleAtEpochMillis: Long = Long.MAX_VALUE): PendingSyncBatch
    suspend fun nextRetryAtEpochMillis(): Long?
    suspend fun syncAttemptCount(): Int
    suspend fun syncGeneration(): Long
    suspend fun acknowledgeSyncOperations(operationIds: List<Long>)
    suspend fun recordSyncFailure(
        operationIds: List<Long>,
        failure: SyncFailure,
        nextRetryAtEpochMillis: Long,
        expectedGeneration: Long,
    ): Boolean
    suspend fun retrySyncNow()
    suspend fun commitRemoteCopy(copy: InitialAccountCopy, acknowledgedOperationIds: List<Long>)
    suspend fun commitRemoteSnapshot(snapshot: RemoteSyncSnapshot, acknowledgedOperationIds: List<Long>) {
        commitRemoteCopy(snapshot.copy, acknowledgedOperationIds)
    }
}

data class SyncSession(
    val accountId: String,
    val accessToken: String,
)

interface SyncSessionProvider {
    suspend fun currentSession(): SyncSession?
    suspend fun refreshSession(expiredSession: SyncSession): SyncSession? = null
    suspend fun requireReauthentication() = Unit
}

interface SyncBackend {
    suspend fun push(session: SyncSession, batch: PendingSyncBatch)
    suspend fun pull(session: SyncSession): InitialAccountCopy
    suspend fun pullVersioned(session: SyncSession): RemoteSyncSnapshot = RemoteSyncSnapshot(
        copy = pull(session),
        versions = emptyList(),
    )
}

class SyncNetworkException(message: String? = null) : Exception(message)
class SyncConflictException(message: String? = null) : Exception(message)
class SyncServerException(message: String? = null) : Exception(message)

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
    private val currentTimeMillis: (() -> Long)? = null,
    private val operationGate: AccountOperationGate? = null,
) {
    suspend fun runOnce(): SyncRunResult {
        val lease = operationGate?.acquire()
        if (operationGate != null && lease == null) return SyncRunResult.SignedOut
        var session = when (val acquired = runGuarded(lease) { sessionProvider.currentSession() }) {
            is AccountLeaseResult.Completed -> acquired.value ?: return SyncRunResult.SignedOut
            AccountLeaseResult.Revoked -> return SyncRunResult.SignedOut
        }
        val generation = localData.syncGeneration()
        var refreshed = false
        var hasPulled = false
        var downloadedSessionCount = 0
        repeat(MAX_BATCHES_PER_RUN) {
            val now = currentTimeMillis?.invoke()
            if (now != null) {
                localData.nextRetryAtEpochMillis()?.takeIf { it > now }?.let { retryAt ->
                    return SyncRunResult.Retry(retryAt - now)
                }
            }
            val batch = localData.pendingSyncBatch(eligibleAtEpochMillis = now ?: Long.MAX_VALUE)
            if (batch.operations.isEmpty() && hasPulled) {
                return SyncRunResult.Synchronized(downloadedSessionCount)
            }
            try {
                if (batch.operations.isNotEmpty()) {
                    if (runGuarded(lease) { backend.push(session, batch) } is AccountLeaseResult.Revoked) {
                        return SyncRunResult.SignedOut
                    }
                }
                val remoteSnapshot = when (val pulled = runGuarded(lease) { backend.pullVersioned(session) }) {
                    is AccountLeaseResult.Completed -> pulled.value
                    AccountLeaseResult.Revoked -> return SyncRunResult.SignedOut
                }
                if (
                    runGuarded(lease) {
                        localData.commitRemoteSnapshot(remoteSnapshot, batch.operations.map(SyncOperation::id))
                    } is AccountLeaseResult.Revoked
                ) {
                    return SyncRunResult.SignedOut
                }
                downloadedSessionCount = remoteSnapshot.copy.sessions.size
                hasPulled = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: CloudAuthenticationExpiredException) {
                if (refreshed) {
                    sessionProvider.requireReauthentication()
                    return SyncRunResult.SignedOut
                }
                session = sessionProvider.refreshSession(session) ?: run {
                    sessionProvider.requireReauthentication()
                    return SyncRunResult.SignedOut
                }
                refreshed = true
            } catch (error: Exception) {
                val operationIds = batch.operations.map(SyncOperation::id)
                val attempt = maxOf(
                    batch.operations.maxOfOrNull(SyncOperation::attemptCount) ?: 0,
                    localData.syncAttemptCount(),
                ) + 1
                val delay = retryPolicy.delayMillis(attempt)
                val recorded = when (
                    val failure = runGuarded(lease) {
                        localData.recordSyncFailure(
                            operationIds = operationIds,
                            failure = error.toSyncFailure(),
                            nextRetryAtEpochMillis = (currentTimeMillis?.invoke() ?: 0L) + delay,
                            expectedGeneration = generation,
                        )
                    }
                ) {
                    is AccountLeaseResult.Completed -> failure.value
                    AccountLeaseResult.Revoked -> return SyncRunResult.SignedOut
                }
                return SyncRunResult.Retry(if (recorded) delay else 0L)
            }
        }
        return SyncRunResult.Retry(retryPolicy.delayMillis(1))
    }

    private suspend fun <T> runGuarded(
        lease: AccountOperationLease?,
        operation: suspend () -> T,
    ): AccountLeaseResult<T> = operationGate?.runIfCurrent(requireNotNull(lease), operation)
        ?: AccountLeaseResult.Completed(operation())

    private companion object {
        const val MAX_BATCHES_PER_RUN = 20
    }
}

interface SyncWorkScheduler {
    fun appendImmediate()
    fun replaceDeadline(deadlineEpochMillis: Long)
    fun cancelDeadline()
}

class ScheduledSyncExecution(
    private val coordinator: SyncCoordinator,
    private val scheduler: SyncWorkScheduler,
    private val currentTimeMillis: () -> Long,
) {
    suspend fun runOnce(): SyncRunResult = coordinator.runOnce().also { result ->
        when (result) {
            is SyncRunResult.Retry -> if (result.afterMillis > 0L) {
                scheduler.replaceDeadline(currentTimeMillis() + result.afterMillis)
            } else {
                scheduler.cancelDeadline()
                scheduler.appendImmediate()
            }
            SyncRunResult.SignedOut,
            is SyncRunResult.Synchronized,
            -> scheduler.cancelDeadline()
        }
    }

    fun requestImmediate() {
        scheduler.cancelDeadline()
        scheduler.appendImmediate()
    }
}

private fun Exception.toSyncFailure() = when (this) {
    is SyncNetworkException -> SyncFailure(SyncFailureCategory.NETWORK, message)
    is SyncConflictException -> SyncFailure(SyncFailureCategory.CONFLICT, message)
    is SyncServerException -> SyncFailure(SyncFailureCategory.SERVER, message)
    else -> SyncFailure(SyncFailureCategory.UNKNOWN, message)
}
