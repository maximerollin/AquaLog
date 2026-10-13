package com.maximerollin.aqualog.shared

import androidx.room3.ConstructedBy
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aquariums")
data class AquariumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val volume: Double,
    val volumeUnit: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'established'") val profile: String,
)

@Entity(tableName = "parameter_definitions")
data class ParameterDefinitionEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val parameter: String,
    val isActive: Boolean,
    val position: Int,
    val unit: String,
    val precision: Int,
    val indicativeMinimum: Double?,
    val indicativeMaximum: Double?,
)

data class AquariumSetupEntity(
    @Embedded val aquarium: AquariumEntity,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["aquariumId"],
    )
    val parameters: List<ParameterDefinitionEntity>,
)

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["aquariumId", "occurredAtEpochMillis"]),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val idempotencyKey: String,
)

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val parameterDefinitionId: String,
    val value: Double,
)

@Entity(tableName = "maintenance_actions")
data class MaintenanceActionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val quantity: Double?,
    val unit: String?,
    val product: String?,
)

@Entity(tableName = "session_events")
data class SessionEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val type: String,
    val note: String,
)

data class RecordedSessionEntity(
    @Embedded val session: SessionEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["sessionId"])
    val measurements: List<MeasurementEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["sessionId"])
    val maintenanceActions: List<MaintenanceActionEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["sessionId"])
    val events: List<SessionEventEntity>,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val title: String,
    val recurrence: String,
    val monthlyAnchorDay: Int,
)

@Entity(
    tableName = "task_occurrences",
    indices = [
        Index(value = ["taskId", "dueYear", "dueMonth", "dueDay", "dueMinuteOfDay", "timeZoneId"], unique = true),
    ],
)
data class TaskOccurrenceEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val aquariumId: String,
    val title: String,
    val dueYear: Int,
    val dueMonth: Int,
    val dueDay: Int,
    val dueMinuteOfDay: Int,
    val timeZoneId: String,
    val resolution: String?,
    val resolvedAtEpochMillis: Long?,
)

@Entity(tableName = "account_state")
data class AccountStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val accountId: String,
    val migrationStatus: String,
)

@Entity(tableName = "account_invitation")
data class AccountInvitationEntity(
    @PrimaryKey val singletonId: Int = 1,
    val wasOffered: Boolean,
)

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationKey: String = "",
    val entityType: String,
    val entityId: String,
    @ColumnInfo(defaultValue = "'upsert'") val kind: String = "upsert",
    val attemptCount: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0L,
    val failureCategory: String? = null,
    val failureDetail: String? = null,
    val clientUpdatedAtEpochMillis: Long = 0L,
)

@Entity(
    tableName = "sync_entity_versions",
    primaryKeys = ["entityType", "entityId"],
)
data class SyncEntityVersionEntity(
    val entityType: String,
    val entityId: String,
    val serverVersion: Long,
    val serverUpdatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)

@Entity(tableName = "sync_runtime_state")
data class SyncRuntimeStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val failureCategory: String,
    val failureDetail: String?,
)

@Dao
abstract class AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract fun observeCurrent(): Flow<AquariumEntity?>

    @Transaction
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract fun observeCurrentSetup(): Flow<AquariumSetupEntity?>

    @Transaction
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC")
    abstract fun observeAllSetups(): Flow<List<AquariumSetupEntity>>

    @Transaction
    @Query("SELECT * FROM aquariums WHERE id = :aquariumId")
    abstract suspend fun getSetup(aquariumId: String): AquariumSetupEntity?

    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC")
    abstract suspend fun allAquariums(): List<AquariumEntity>

    @Query("SELECT * FROM parameter_definitions ORDER BY aquariumId, position ASC")
    abstract suspend fun allParameterDefinitions(): List<ParameterDefinitionEntity>

    @Insert
    protected abstract suspend fun insert(aquarium: AquariumEntity)

    @Insert
    protected abstract suspend fun insertParameters(parameters: List<ParameterDefinitionEntity>)

    @Insert
    protected abstract suspend fun insertSyncOperations(operations: List<SyncOperationEntity>)

    @Upsert
    protected abstract suspend fun upsertAquarium(aquarium: AquariumEntity)

    @Upsert
    protected abstract suspend fun upsertParameter(parameter: ParameterDefinitionEntity)

    @Transaction
    open suspend fun create(aquarium: AquariumEntity, syncOperation: SyncOperationEntity) {
        insert(aquarium)
        insertSyncOperations(listOf(syncOperation))
    }

    @Transaction
    open suspend fun createConfigured(
        aquarium: AquariumEntity,
        parameters: List<ParameterDefinitionEntity>,
        syncOperations: List<SyncOperationEntity>,
    ) {
        insert(aquarium)
        insertParameters(parameters)
        insertSyncOperations(syncOperations)
    }

    @Transaction
    open suspend fun updateAquarium(aquarium: AquariumEntity, syncOperation: SyncOperationEntity) {
        requireNotNull(getSetup(aquarium.id)) { "Aquarium does not exist" }
        upsertAquarium(aquarium)
        insertSyncOperations(listOf(syncOperation))
    }

    @Transaction
    open suspend fun updateParameter(parameter: ParameterDefinitionEntity, syncOperation: SyncOperationEntity) {
        requireNotNull(getSetup(parameter.aquariumId)) { "Aquarium does not exist" }
        upsertParameter(parameter)
        insertSyncOperations(listOf(syncOperation))
    }
}

@Dao
abstract class SessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert
    protected abstract suspend fun insertMeasurements(measurements: List<MeasurementEntity>)

    @Insert
    protected abstract suspend fun insertMaintenanceActions(actions: List<MaintenanceActionEntity>)

    @Insert
    protected abstract suspend fun insertEvents(events: List<SessionEventEntity>)

    @Insert
    protected abstract suspend fun insertSyncOperations(operations: List<SyncOperationEntity>)

    @Transaction
    @Query("SELECT * FROM sessions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    protected abstract suspend fun getByIdempotencyKey(idempotencyKey: String): RecordedSessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    abstract suspend fun getById(sessionId: String): RecordedSessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    abstract fun observeById(sessionId: String): Flow<RecordedSessionEntity?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE aquariumId = :aquariumId ORDER BY occurredAtEpochMillis DESC, createdAtEpochMillis DESC LIMIT 1")
    abstract fun observeLatest(aquariumId: String): Flow<RecordedSessionEntity?>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY occurredAtEpochMillis DESC, createdAtEpochMillis DESC")
    abstract fun observeAll(): Flow<List<RecordedSessionEntity>>

    @Transaction
    @Query(
        "SELECT * FROM sessions WHERE aquariumId = :aquariumId " +
            "AND occurredAtEpochMillis BETWEEN :sinceEpochMillisInclusive AND :untilEpochMillisInclusive " +
            "ORDER BY occurredAtEpochMillis ASC, createdAtEpochMillis ASC",
    )
    abstract fun observeForAquariumBetween(
        aquariumId: String,
        sinceEpochMillisInclusive: Long,
        untilEpochMillisInclusive: Long,
    ): Flow<List<RecordedSessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions WHERE aquariumId = :aquariumId")
    abstract suspend fun count(aquariumId: String): Int

    @Query("SELECT COUNT(*) FROM sessions")
    abstract suspend fun countAll(): Int

    @Query("SELECT * FROM sessions ORDER BY createdAtEpochMillis ASC")
    abstract suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM measurements ORDER BY sessionId, id")
    abstract suspend fun allMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM maintenance_actions ORDER BY sessionId, id")
    abstract suspend fun allMaintenanceActions(): List<MaintenanceActionEntity>

    @Query("SELECT * FROM session_events ORDER BY sessionId, id")
    abstract suspend fun allEvents(): List<SessionEventEntity>

    @Query("SELECT m.* FROM measurements m INNER JOIN sessions s ON s.id = m.sessionId WHERE s.aquariumId = :aquariumId ORDER BY s.occurredAtEpochMillis DESC, s.createdAtEpochMillis DESC")
    abstract suspend fun measurementsForAquarium(aquariumId: String): List<MeasurementEntity>

    @Query("SELECT a.* FROM maintenance_actions a INNER JOIN sessions s ON s.id = a.sessionId WHERE s.aquariumId = :aquariumId ORDER BY s.occurredAtEpochMillis DESC, s.createdAtEpochMillis DESC")
    abstract suspend fun maintenanceActionsForAquarium(aquariumId: String): List<MaintenanceActionEntity>

    @Query("UPDATE sessions SET occurredAtEpochMillis = :occurredAtEpochMillis WHERE id = :sessionId AND aquariumId = :aquariumId")
    protected abstract suspend fun updateOccurredAt(
        sessionId: String,
        aquariumId: String,
        occurredAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM measurements WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteMeasurements(sessionId: String)

    @Query("DELETE FROM maintenance_actions WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteMaintenanceActions(sessionId: String)

    @Query("DELETE FROM session_events WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteEvents(sessionId: String)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    protected abstract suspend fun deleteSessionRow(sessionId: String): Int

    @Transaction
    open suspend fun create(
        session: SessionEntity,
        measurements: List<MeasurementEntity>,
        actions: List<MaintenanceActionEntity>,
        events: List<SessionEventEntity>,
        syncOperations: List<SyncOperationEntity>,
    ): RecordedSessionEntity {
        val inserted = insertSession(session) != -1L
        if (inserted) {
            if (measurements.isNotEmpty()) insertMeasurements(measurements)
            if (actions.isNotEmpty()) insertMaintenanceActions(actions)
            if (events.isNotEmpty()) insertEvents(events)
            insertSyncOperations(syncOperations)
        }
        return requireNotNull(getByIdempotencyKey(session.idempotencyKey))
    }

    @Transaction
    open suspend fun update(
        sessionId: String,
        aquariumId: String,
        occurredAtEpochMillis: Long,
        measurements: List<MeasurementEntity>,
        actions: List<MaintenanceActionEntity>,
        events: List<SessionEventEntity>,
        syncOperations: List<SyncOperationEntity>,
    ): RecordedSessionEntity {
        require(updateOccurredAt(sessionId, aquariumId, occurredAtEpochMillis) == 1) {
            "Session does not exist in this Aquarium"
        }
        deleteMeasurements(sessionId)
        deleteMaintenanceActions(sessionId)
        deleteEvents(sessionId)
        if (measurements.isNotEmpty()) insertMeasurements(measurements)
        if (actions.isNotEmpty()) insertMaintenanceActions(actions)
        if (events.isNotEmpty()) insertEvents(events)
        insertSyncOperations(syncOperations)
        return requireNotNull(getById(sessionId))
    }

    @Transaction
    open suspend fun delete(sessionId: String, syncOperations: List<SyncOperationEntity>) {
        requireNotNull(getById(sessionId)) { "Session does not exist" }
        deleteMeasurements(sessionId)
        deleteMaintenanceActions(sessionId)
        deleteEvents(sessionId)
        check(deleteSessionRow(sessionId) == 1)
        insertSyncOperations(syncOperations)
    }
}

@Dao
abstract class TaskDao {
    @Insert
    protected abstract suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertOccurrence(occurrence: TaskOccurrenceEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    protected abstract suspend fun getTask(taskId: String): TaskEntity?

    @Query("SELECT * FROM task_occurrences WHERE id = :occurrenceId")
    protected abstract suspend fun getOccurrence(occurrenceId: String): TaskOccurrenceEntity?

    @Query("SELECT * FROM task_occurrences WHERE taskId = :taskId AND dueYear = :year AND dueMonth = :month AND dueDay = :day AND dueMinuteOfDay = :minute AND timeZoneId = :timeZoneId LIMIT 1")
    protected abstract suspend fun getOccurrenceByDue(
        taskId: String,
        year: Int,
        month: Int,
        day: Int,
        minute: Int,
        timeZoneId: String,
    ): TaskOccurrenceEntity?

    @Query("UPDATE task_occurrences SET resolution = :resolution, resolvedAtEpochMillis = :resolvedAt WHERE id = :occurrenceId AND resolution IS NULL")
    protected abstract suspend fun markResolved(occurrenceId: String, resolution: String, resolvedAt: Long): Int

    @Query("SELECT * FROM task_occurrences WHERE resolution IS NULL ORDER BY dueYear, dueMonth, dueDay, dueMinuteOfDay")
    abstract fun observePending(): Flow<List<TaskOccurrenceEntity>>

    @Query("SELECT * FROM task_occurrences WHERE resolution IS NOT NULL ORDER BY resolvedAtEpochMillis DESC")
    abstract fun observeResolved(): Flow<List<TaskOccurrenceEntity>>

    @Transaction
    open suspend fun create(task: TaskEntity, occurrence: TaskOccurrenceEntity) {
        insertTask(task)
        check(insertOccurrence(occurrence) != -1L)
    }

    @Transaction
    open suspend fun resolve(
        occurrenceId: String,
        resolution: String,
        resolvedAt: Long,
        nextOccurrence: TaskOccurrenceEntity?,
    ): Pair<TaskOccurrenceEntity, TaskOccurrenceEntity?> {
        val current = requireNotNull(getOccurrence(occurrenceId)) { "Occurrence does not exist" }
        require(current.resolution == null) { "Occurrence is already resolved" }
        check(markResolved(occurrenceId, resolution, resolvedAt) == 1)
        val resolved = requireNotNull(getOccurrence(occurrenceId))
        val next = nextOccurrence?.let { candidate ->
            insertOccurrence(candidate)
            requireNotNull(
                getOccurrenceByDue(
                    candidate.taskId,
                    candidate.dueYear,
                    candidate.dueMonth,
                    candidate.dueDay,
                    candidate.dueMinuteOfDay,
                    candidate.timeZoneId,
                ),
            )
        }
        return resolved to next
    }

    suspend fun taskForOccurrence(occurrenceId: String): TaskEntity =
        requireNotNull(getTask(requireNotNull(getOccurrence(occurrenceId)).taskId))
}

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM account_state WHERE singletonId = 1")
    abstract suspend fun get(): AccountStateEntity?

    @Upsert
    abstract suspend fun upsert(state: AccountStateEntity)

    @Query("SELECT wasOffered FROM account_invitation WHERE singletonId = 1")
    abstract suspend fun wasInvitationOffered(): Boolean?

    @Upsert
    abstract suspend fun upsertInvitation(invitation: AccountInvitationEntity)
}

data class PortableDatabaseSnapshot(
    val aquariums: List<AquariumEntity>,
    val parameterDefinitions: List<ParameterDefinitionEntity>,
    val sessions: List<SessionEntity>,
    val measurements: List<MeasurementEntity>,
    val maintenanceActions: List<MaintenanceActionEntity>,
    val events: List<SessionEventEntity>,
    val tasks: List<TaskEntity>,
    val taskOccurrences: List<TaskOccurrenceEntity>,
)

@Dao
abstract class PortableDataDao {
    @Query("SELECT * FROM aquariums")
    protected abstract suspend fun allAquariums(): List<AquariumEntity>

    @Query("SELECT * FROM parameter_definitions")
    protected abstract suspend fun allParameterDefinitions(): List<ParameterDefinitionEntity>

    @Query("SELECT * FROM sessions")
    protected abstract suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM measurements")
    protected abstract suspend fun allMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM maintenance_actions")
    protected abstract suspend fun allMaintenanceActions(): List<MaintenanceActionEntity>

    @Query("SELECT * FROM session_events")
    protected abstract suspend fun allEvents(): List<SessionEventEntity>

    @Query("SELECT * FROM tasks")
    protected abstract suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM task_occurrences")
    protected abstract suspend fun allTaskOccurrences(): List<TaskOccurrenceEntity>

    @Insert
    protected abstract suspend fun insertAquariums(values: List<AquariumEntity>)

    @Insert
    protected abstract suspend fun insertParameterDefinitions(values: List<ParameterDefinitionEntity>)

    @Insert
    protected abstract suspend fun insertSessions(values: List<SessionEntity>)

    @Insert
    protected abstract suspend fun insertMeasurements(values: List<MeasurementEntity>)

    @Insert
    protected abstract suspend fun insertMaintenanceActions(values: List<MaintenanceActionEntity>)

    @Insert
    protected abstract suspend fun insertEvents(values: List<SessionEventEntity>)

    @Insert
    protected abstract suspend fun insertTasks(values: List<TaskEntity>)

    @Insert
    protected abstract suspend fun insertTaskOccurrences(values: List<TaskOccurrenceEntity>)

    @Query("DELETE FROM measurements")
    protected abstract suspend fun deleteMeasurements()

    @Query("DELETE FROM maintenance_actions")
    protected abstract suspend fun deleteMaintenanceActions()

    @Query("DELETE FROM session_events")
    protected abstract suspend fun deleteEvents()

    @Query("DELETE FROM sessions")
    protected abstract suspend fun deleteSessions()

    @Query("DELETE FROM task_occurrences")
    protected abstract suspend fun deleteTaskOccurrences()

    @Query("DELETE FROM tasks")
    protected abstract suspend fun deleteTasks()

    @Query("DELETE FROM parameter_definitions")
    protected abstract suspend fun deleteParameterDefinitions()

    @Query("DELETE FROM aquariums")
    protected abstract suspend fun deleteAquariums()

    @Query("DELETE FROM sync_operations")
    protected abstract suspend fun deleteSyncOperations()

    @Query("SELECT COUNT(*) FROM account_state")
    protected abstract suspend fun accountStateCount(): Int

    @Transaction
    open suspend fun snapshot() = PortableDatabaseSnapshot(
        aquariums = allAquariums(),
        parameterDefinitions = allParameterDefinitions(),
        sessions = allSessions(),
        measurements = allMeasurements(),
        maintenanceActions = allMaintenanceActions(),
        events = allEvents(),
        tasks = allTasks(),
        taskOccurrences = allTaskOccurrences(),
    )

    @Transaction
    open suspend fun replaceAll(snapshot: PortableDatabaseSnapshot): Boolean {
        if (accountStateCount() != 0) return false
        deleteSyncOperations()
        deleteMeasurements()
        deleteMaintenanceActions()
        deleteEvents()
        deleteSessions()
        deleteTaskOccurrences()
        deleteTasks()
        deleteParameterDefinitions()
        deleteAquariums()

        if (snapshot.aquariums.isNotEmpty()) insertAquariums(snapshot.aquariums)
        if (snapshot.parameterDefinitions.isNotEmpty()) insertParameterDefinitions(snapshot.parameterDefinitions)
        if (snapshot.sessions.isNotEmpty()) insertSessions(snapshot.sessions)
        if (snapshot.measurements.isNotEmpty()) insertMeasurements(snapshot.measurements)
        if (snapshot.maintenanceActions.isNotEmpty()) insertMaintenanceActions(snapshot.maintenanceActions)
        if (snapshot.events.isNotEmpty()) insertEvents(snapshot.events)
        if (snapshot.tasks.isNotEmpty()) insertTasks(snapshot.tasks)
        if (snapshot.taskOccurrences.isNotEmpty()) insertTaskOccurrences(snapshot.taskOccurrences)
        return true
    }
}

@Dao
abstract class SyncDao {
    @Query("SELECT * FROM sync_operations ORDER BY id ASC")
    abstract fun observePending(): Flow<List<SyncOperationEntity>>

    @Query("SELECT * FROM sync_operations ORDER BY id ASC LIMIT :limit")
    abstract suspend fun pending(limit: Int): List<SyncOperationEntity>

    @Query(
        "SELECT * FROM sync_operations WHERE nextAttemptAtEpochMillis <= :eligibleAtEpochMillis " +
            "ORDER BY id ASC LIMIT :limit",
    )
    abstract suspend fun pendingEligible(limit: Int, eligibleAtEpochMillis: Long): List<SyncOperationEntity>

    @Query("SELECT MIN(nextAttemptAtEpochMillis) FROM sync_operations WHERE nextAttemptAtEpochMillis > 0")
    abstract suspend fun nextOperationRetryAtEpochMillis(): Long?

    @Query("SELECT * FROM sync_runtime_state WHERE singletonId = 1")
    abstract fun observeRuntimeState(): Flow<SyncRuntimeStateEntity?>

    @Query("SELECT * FROM sync_runtime_state WHERE singletonId = 1")
    abstract suspend fun runtimeState(): SyncRuntimeStateEntity?

    @Upsert
    protected abstract suspend fun upsertRuntimeState(state: SyncRuntimeStateEntity)

    @Query("DELETE FROM sync_runtime_state")
    protected abstract suspend fun clearRuntimeState()

    @Query("DELETE FROM sync_operations WHERE id IN (:operationIds)")
    abstract suspend fun acknowledge(operationIds: List<Long>)

    @Query(
        "UPDATE sync_operations SET attemptCount = attemptCount + 1, " +
            "nextAttemptAtEpochMillis = :nextRetryAtEpochMillis, failureCategory = :category, " +
            "failureDetail = :detail WHERE id IN (:operationIds)",
    )
    protected abstract suspend fun updateOperationFailures(
        operationIds: List<Long>,
        category: String,
        detail: String?,
        nextRetryAtEpochMillis: Long,
    )

    @Query(
        "UPDATE sync_operations SET nextAttemptAtEpochMillis = 0, " +
            "failureCategory = NULL, failureDetail = NULL",
    )
    protected abstract suspend fun resetOperationRetries()

    @Transaction
    open suspend fun recordFailure(
        operationIds: List<Long>,
        category: String,
        detail: String?,
        nextRetryAtEpochMillis: Long,
    ) {
        if (operationIds.isNotEmpty()) {
            updateOperationFailures(operationIds, category, detail, nextRetryAtEpochMillis)
        }
        upsertRuntimeState(
            SyncRuntimeStateEntity(
                attemptCount = (runtimeState()?.attemptCount ?: 0) + 1,
                nextAttemptAtEpochMillis = nextRetryAtEpochMillis,
                failureCategory = category,
                failureDetail = detail,
            ),
        )
    }

    @Transaction
    open suspend fun retryNow() {
        resetOperationRetries()
        clearRuntimeState()
    }

    @Upsert
    protected abstract suspend fun upsertAquariums(aquariums: List<AquariumEntity>)

    @Upsert
    protected abstract suspend fun upsertParameterDefinitions(parameters: List<ParameterDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRemoteSessions(sessions: List<SessionEntity>)

    @Query(
        "UPDATE sessions SET aquariumId = :aquariumId, occurredAtEpochMillis = :occurredAtEpochMillis, " +
            "createdAtEpochMillis = :createdAtEpochMillis WHERE id = :id",
    )
    protected abstract suspend fun updateRemoteSession(
        id: String,
        aquariumId: String,
        occurredAtEpochMillis: Long,
        createdAtEpochMillis: Long,
    )

    @Upsert
    protected abstract suspend fun upsertMeasurements(measurements: List<MeasurementEntity>)

    @Upsert
    protected abstract suspend fun upsertMaintenanceActions(actions: List<MaintenanceActionEntity>)

    @Upsert
    protected abstract suspend fun upsertEvents(events: List<SessionEventEntity>)

    @Query("SELECT * FROM sync_entity_versions")
    protected abstract suspend fun allVersions(): List<SyncEntityVersionEntity>

    @Upsert
    protected abstract suspend fun upsertVersions(versions: List<SyncEntityVersionEntity>)

    @Query("SELECT * FROM measurements")
    protected abstract suspend fun allMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM maintenance_actions")
    protected abstract suspend fun allActions(): List<MaintenanceActionEntity>

    @Query("SELECT * FROM session_events")
    protected abstract suspend fun allEvents(): List<SessionEventEntity>

    @Query("DELETE FROM measurements WHERE id IN (:ids)")
    protected abstract suspend fun deleteMeasurements(ids: List<String>)

    @Query("DELETE FROM maintenance_actions WHERE id IN (:ids)")
    protected abstract suspend fun deleteActions(ids: List<String>)

    @Query("DELETE FROM session_events WHERE id IN (:ids)")
    protected abstract suspend fun deleteEvents(ids: List<String>)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    protected abstract suspend fun deleteSessions(ids: List<String>)

    @Query("DELETE FROM parameter_definitions WHERE id IN (:ids)")
    protected abstract suspend fun deleteParameters(ids: List<String>)

    @Query("DELETE FROM aquariums WHERE id IN (:ids)")
    protected abstract suspend fun deleteAquariums(ids: List<String>)

    @Transaction
    open suspend fun applyRemoteCopy(
        aquariums: List<AquariumEntity>,
        parameters: List<ParameterDefinitionEntity>,
        sessions: List<SessionEntity>,
        measurements: List<MeasurementEntity>,
        actions: List<MaintenanceActionEntity>,
        events: List<SessionEventEntity>,
        acknowledgedOperationIds: List<Long>,
    ) {
        if (acknowledgedOperationIds.isNotEmpty()) acknowledge(acknowledgedOperationIds)
        clearRuntimeState()
        val dirty = pending(Int.MAX_VALUE).map { it.entityType to it.entityId }.toSet()
        val cleanAquariums = aquariums.filterNot { SyncEntityType.AQUARIUM.storageValue to it.id in dirty }
        val cleanParameters = parameters.filterNot { SyncEntityType.PARAMETER_DEFINITION.storageValue to it.id in dirty }
        val cleanSessions = sessions.filterNot { SyncEntityType.SESSION.storageValue to it.id in dirty }
        val cleanMeasurements = measurements.filterNot { SyncEntityType.MEASUREMENT.storageValue to it.id in dirty }
        val cleanActions = actions.filterNot { SyncEntityType.MAINTENANCE_ACTION.storageValue to it.id in dirty }
        val cleanEvents = events.filterNot { SyncEntityType.EVENT.storageValue to it.id in dirty }
        if (cleanAquariums.isNotEmpty()) upsertAquariums(cleanAquariums)
        if (cleanParameters.isNotEmpty()) upsertParameterDefinitions(cleanParameters)
        if (cleanSessions.isNotEmpty()) {
            insertRemoteSessions(cleanSessions)
            cleanSessions.forEach { session ->
                updateRemoteSession(
                    session.id,
                    session.aquariumId,
                    session.occurredAtEpochMillis,
                    session.createdAtEpochMillis,
                )
            }
        }
        if (cleanMeasurements.isNotEmpty()) upsertMeasurements(cleanMeasurements)
        if (cleanActions.isNotEmpty()) upsertMaintenanceActions(cleanActions)
        if (cleanEvents.isNotEmpty()) upsertEvents(cleanEvents)

    }

    @Transaction
    open suspend fun applyRemoteSnapshot(
        aquariums: List<AquariumEntity>,
        parameters: List<ParameterDefinitionEntity>,
        sessions: List<SessionEntity>,
        measurements: List<MeasurementEntity>,
        actions: List<MaintenanceActionEntity>,
        events: List<SessionEventEntity>,
        versions: List<SyncEntityVersionEntity>,
        acknowledgedOperationIds: List<Long>,
    ) {
        if (acknowledgedOperationIds.isNotEmpty()) acknowledge(acknowledgedOperationIds)
        clearRuntimeState()
        val dirty = pending(Int.MAX_VALUE).map { it.entityType to it.entityId }.toSet()
        val storedVersions = allVersions().associateBy { it.entityType to it.entityId }
        val acceptedVersions = versions.filter { incoming ->
            val key = incoming.entityType to incoming.entityId
            key !in dirty && incoming.serverVersion > (storedVersions[key]?.serverVersion ?: Long.MIN_VALUE)
        }
        val acceptedLive = acceptedVersions.filter { it.deletedAtEpochMillis == null }
            .mapTo(mutableSetOf()) { it.entityType to it.entityId }
        fun accepted(type: SyncEntityType, id: String) = type.storageValue to id in acceptedLive

        aquariums.filter { accepted(SyncEntityType.AQUARIUM, it.id) }
            .takeIf(List<AquariumEntity>::isNotEmpty)?.let { upsertAquariums(it) }
        parameters.filter { accepted(SyncEntityType.PARAMETER_DEFINITION, it.id) }
            .takeIf(List<ParameterDefinitionEntity>::isNotEmpty)?.let { upsertParameterDefinitions(it) }
        sessions.filter { accepted(SyncEntityType.SESSION, it.id) }.forEach { session ->
            insertRemoteSessions(listOf(session))
            updateRemoteSession(
                session.id,
                session.aquariumId,
                session.occurredAtEpochMillis,
                session.createdAtEpochMillis,
            )
        }
        measurements.filter { accepted(SyncEntityType.MEASUREMENT, it.id) }
            .takeIf(List<MeasurementEntity>::isNotEmpty)?.let { upsertMeasurements(it) }
        actions.filter { accepted(SyncEntityType.MAINTENANCE_ACTION, it.id) }
            .takeIf(List<MaintenanceActionEntity>::isNotEmpty)?.let { upsertMaintenanceActions(it) }
        events.filter { accepted(SyncEntityType.EVENT, it.id) }
            .takeIf(List<SessionEventEntity>::isNotEmpty)?.let { upsertEvents(it) }

        val tombstones = acceptedVersions.filter { it.deletedAtEpochMillis != null }.groupBy { it.entityType }
        tombstones[SyncEntityType.MEASUREMENT.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteMeasurements(it) }
        tombstones[SyncEntityType.MAINTENANCE_ACTION.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteActions(it) }
        tombstones[SyncEntityType.EVENT.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteEvents(it) }
        tombstones[SyncEntityType.SESSION.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteSessions(it) }
        tombstones[SyncEntityType.PARAMETER_DEFINITION.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteParameters(it) }
        tombstones[SyncEntityType.AQUARIUM.storageValue]?.map(SyncEntityVersionEntity::entityId)
            ?.takeIf(List<String>::isNotEmpty)?.let { deleteAquariums(it) }
        if (acceptedVersions.isNotEmpty()) upsertVersions(acceptedVersions)
    }
}

@Database(
    entities = [
        AquariumEntity::class,
        ParameterDefinitionEntity::class,
        SessionEntity::class,
        MeasurementEntity::class,
        MaintenanceActionEntity::class,
        SessionEventEntity::class,
        TaskEntity::class,
        TaskOccurrenceEntity::class,
        AccountStateEntity::class,
        AccountInvitationEntity::class,
        SyncOperationEntity::class,
        SyncEntityVersionEntity::class,
        SyncRuntimeStateEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@ConstructedBy(AquaLogDatabaseConstructor::class)
abstract class AquaLogDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
    abstract fun sessionDao(): SessionDao
    abstract fun taskDao(): TaskDao
    abstract fun accountDao(): AccountDao
    abstract fun syncDao(): SyncDao
    abstract fun portableDataDao(): PortableDataDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AquaLogDatabaseConstructor : RoomDatabaseConstructor<AquaLogDatabase> {
    override fun initialize(): AquaLogDatabase
}

fun createAquaLogDatabase(
    builder: RoomDatabase.Builder<AquaLogDatabase>,
): AquaLogDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
    )
    .build()

private val MIGRATION_1_2 = Migration(1, 2) { connection ->
    connection.execSQL(
        "ALTER TABLE aquariums ADD COLUMN profile TEXT NOT NULL DEFAULT 'established'",
    )
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS parameter_definitions (
            id TEXT NOT NULL PRIMARY KEY,
            aquariumId TEXT NOT NULL,
            parameter TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            position INTEGER NOT NULL,
            unit TEXT NOT NULL,
            precision INTEGER NOT NULL,
            indicativeMinimum REAL,
            indicativeMaximum REAL
        )
        """.trimIndent(),
    )
}

private val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS sessions (id TEXT NOT NULL PRIMARY KEY, aquariumId TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, idempotencyKey TEXT NOT NULL)",
    )
    connection.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sessions_idempotencyKey ON sessions (idempotencyKey)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS measurements (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, parameterDefinitionId TEXT NOT NULL, value REAL NOT NULL)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS maintenance_actions (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, type TEXT NOT NULL, quantity REAL, unit TEXT, product TEXT)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS session_events (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, type TEXT NOT NULL, note TEXT NOT NULL)",
    )
}

private val MIGRATION_3_4 = Migration(3, 4) { connection ->
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS index_sessions_aquariumId_occurredAtEpochMillis " +
            "ON sessions (aquariumId, occurredAtEpochMillis)",
    )
}

private val MIGRATION_4_5 = Migration(4, 5) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS account_state (singletonId INTEGER NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, migrationStatus TEXT NOT NULL)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS account_invitation (singletonId INTEGER NOT NULL PRIMARY KEY, wasOffered INTEGER NOT NULL)",
    )
}

private val MIGRATION_5_6 = Migration(5, 6) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, aquariumId TEXT NOT NULL, title TEXT NOT NULL, recurrence TEXT NOT NULL, monthlyAnchorDay INTEGER NOT NULL)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS task_occurrences (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, aquariumId TEXT NOT NULL, title TEXT NOT NULL, dueYear INTEGER NOT NULL, dueMonth INTEGER NOT NULL, dueDay INTEGER NOT NULL, dueMinuteOfDay INTEGER NOT NULL, timeZoneId TEXT NOT NULL, resolution TEXT, resolvedAtEpochMillis INTEGER)",
    )
    connection.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_task_occurrences_taskId_dueYear_dueMonth_dueDay_dueMinuteOfDay_timeZoneId ON task_occurrences (taskId, dueYear, dueMonth, dueDay, dueMinuteOfDay, timeZoneId)",
    )
}

private val MIGRATION_6_7 = Migration(6, 7) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS sync_operations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, attemptCount INTEGER NOT NULL)",
    )
}

private val MIGRATION_7_8 = Migration(7, 8) { connection ->
    connection.execSQL(
        "ALTER TABLE sync_operations ADD COLUMN kind TEXT NOT NULL DEFAULT 'upsert'",
    )
}

private val MIGRATION_8_9 = Migration(8, 9) { connection ->
    connection.execSQL(
        "ALTER TABLE sync_operations ADD COLUMN operationKey TEXT NOT NULL DEFAULT ''",
    )
    connection.execSQL(
        "UPDATE sync_operations SET operationKey = 'legacy-' || entityType || '-' || entityId || '-' || id",
    )
    connection.execSQL(
        "ALTER TABLE sync_operations ADD COLUMN nextAttemptAtEpochMillis INTEGER NOT NULL DEFAULT 0",
    )
    connection.execSQL("ALTER TABLE sync_operations ADD COLUMN failureCategory TEXT")
    connection.execSQL("ALTER TABLE sync_operations ADD COLUMN failureDetail TEXT")
    connection.execSQL(
        "ALTER TABLE sync_operations ADD COLUMN clientUpdatedAtEpochMillis INTEGER NOT NULL DEFAULT 0",
    )
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_entity_versions (
            entityType TEXT NOT NULL,
            entityId TEXT NOT NULL,
            serverVersion INTEGER NOT NULL,
            serverUpdatedAtEpochMillis INTEGER NOT NULL,
            deletedAtEpochMillis INTEGER,
            PRIMARY KEY(entityType, entityId)
        )
        """.trimIndent(),
    )
    connection.execSQL(
        """
        CREATE TABLE IF NOT EXISTS sync_runtime_state (
            singletonId INTEGER NOT NULL PRIMARY KEY,
            attemptCount INTEGER NOT NULL,
            nextAttemptAtEpochMillis INTEGER NOT NULL,
            failureCategory TEXT NOT NULL,
            failureDetail TEXT
        )
        """.trimIndent(),
    )
}
