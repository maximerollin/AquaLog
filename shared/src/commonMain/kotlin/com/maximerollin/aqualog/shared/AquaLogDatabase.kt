package com.maximerollin.aqualog.shared

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "aquariums")
data class AquariumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val volume: Double,
    val volumeUnit: String,
    val createdAtEpochMillis: Long,
)

@Dao
abstract class AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract fun observeCurrent(): Flow<AquariumEntity?>

    @Insert
    protected abstract suspend fun insert(aquarium: AquariumEntity)

    @Transaction
    open suspend fun create(aquarium: AquariumEntity) {
        insert(aquarium)
    }
}

@Database(
    entities = [AquariumEntity::class],
    version = 1,
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
    .build()
