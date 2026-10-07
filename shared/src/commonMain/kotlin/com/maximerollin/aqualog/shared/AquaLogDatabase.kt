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

    @Insert
    protected abstract suspend fun insert(aquarium: AquariumEntity)

    @Insert
    protected abstract suspend fun insertParameters(parameters: List<ParameterDefinitionEntity>)

    @Transaction
    open suspend fun create(aquarium: AquariumEntity) {
        insert(aquarium)
    }

    @Transaction
    open suspend fun createConfigured(
        aquarium: AquariumEntity,
        parameters: List<ParameterDefinitionEntity>,
    ) {
        insert(aquarium)
        insertParameters(parameters)
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

    @Transaction
    @Query("SELECT * FROM sessions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    protected abstract suspend fun getByIdempotencyKey(idempotencyKey: String): RecordedSessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    protected abstract suspend fun getById(sessionId: String): RecordedSessionEntity?

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
    ): RecordedSessionEntity {
        val inserted = insertSession(session) != -1L
        if (inserted) {
            if (measurements.isNotEmpty()) insertMeasurements(measurements)
            if (actions.isNotEmpty()) insertMaintenanceActions(actions)
            if (events.isNotEmpty()) insertEvents(events)
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
        return requireNotNull(getById(sessionId))
    }

    @Transaction
    open suspend fun delete(sessionId: String) {
        requireNotNull(getById(sessionId)) { "Session does not exist" }
        deleteMeasurements(sessionId)
        deleteMaintenanceActions(sessionId)
        deleteEvents(sessionId)
        check(deleteSessionRow(sessionId) == 1)
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
    ],
    version = 5,
    exportSchema = true,
)
@ConstructedBy(AquaLogDatabaseConstructor::class)
abstract class AquaLogDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
    abstract fun sessionDao(): SessionDao
    abstract fun taskDao(): TaskDao
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
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
        "CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, aquariumId TEXT NOT NULL, title TEXT NOT NULL, recurrence TEXT NOT NULL, monthlyAnchorDay INTEGER NOT NULL)",
    )
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS task_occurrences (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, aquariumId TEXT NOT NULL, title TEXT NOT NULL, dueYear INTEGER NOT NULL, dueMonth INTEGER NOT NULL, dueDay INTEGER NOT NULL, dueMinuteOfDay INTEGER NOT NULL, timeZoneId TEXT NOT NULL, resolution TEXT, resolvedAtEpochMillis INTEGER)",
    )
    connection.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_task_occurrences_taskId_dueYear_dueMonth_dueDay_dueMinuteOfDay_timeZoneId ON task_occurrences (taskId, dueYear, dueMonth, dueDay, dueMinuteOfDay, timeZoneId)",
    )
}
