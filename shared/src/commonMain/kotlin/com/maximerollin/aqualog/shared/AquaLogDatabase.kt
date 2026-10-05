package com.maximerollin.aqualog.shared

import androidx.room3.ConstructedBy
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Insert
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

@Dao
abstract class AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract fun observeCurrent(): Flow<AquariumEntity?>

    @Transaction
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract fun observeCurrentSetup(): Flow<AquariumSetupEntity?>

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

@Database(
    entities = [AquariumEntity::class, ParameterDefinitionEntity::class],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(AquaLogDatabaseConstructor::class)
abstract class AquaLogDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
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
    .addMigrations(MIGRATION_1_2)
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
