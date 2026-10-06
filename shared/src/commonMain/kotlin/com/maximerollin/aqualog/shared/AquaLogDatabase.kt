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
    indices = [Index(value = ["idempotencyKey"], unique = true)],
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

@Entity(tableName = "account_state")
data class AccountStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val accountId: String,
    val migrationStatus: String,
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
    @Query("SELECT * FROM sessions WHERE aquariumId = :aquariumId ORDER BY occurredAtEpochMillis DESC, createdAtEpochMillis DESC LIMIT 1")
    abstract fun observeLatest(aquariumId: String): Flow<RecordedSessionEntity?>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY occurredAtEpochMillis DESC, createdAtEpochMillis DESC")
    abstract fun observeAll(): Flow<List<RecordedSessionEntity>>

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
}

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM account_state WHERE singletonId = 1")
    abstract suspend fun get(): AccountStateEntity?

    @Upsert
    abstract suspend fun upsert(state: AccountStateEntity)
}

@Database(
    entities = [
        AquariumEntity::class,
        ParameterDefinitionEntity::class,
        SessionEntity::class,
        MeasurementEntity::class,
        MaintenanceActionEntity::class,
        SessionEventEntity::class,
        AccountStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(AquaLogDatabaseConstructor::class)
abstract class AquaLogDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
    abstract fun sessionDao(): SessionDao
    abstract fun accountDao(): AccountDao
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
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
        "CREATE TABLE IF NOT EXISTS account_state (singletonId INTEGER NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, migrationStatus TEXT NOT NULL)",
    )
}
