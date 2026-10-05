package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AquariumRepository {
    fun observeCurrentAquarium(): Flow<Aquarium?>

    suspend fun createAquarium(
        name: String,
        volume: Double,
        volumeUnit: VolumeUnit,
    ): Aquarium
}

class RoomAquariumRepository(
    database: AquaLogDatabase,
    private val generateId: () -> String,
    private val currentTimeMillis: () -> Long,
) : AquariumRepository {
    private val aquariumDao = database.aquariumDao()

    override fun observeCurrentAquarium(): Flow<Aquarium?> =
        aquariumDao.observeCurrent().map { it?.toDomain() }

    override suspend fun createAquarium(
        name: String,
        volume: Double,
        volumeUnit: VolumeUnit,
    ): Aquarium {
        require(name.isNotBlank()) { "Aquarium name must not be blank" }
        require(volume > 0.0 && volume.isFinite()) { "Aquarium volume must be positive" }

        val aquarium = Aquarium(
            id = generateId(),
            name = name.trim(),
            volume = volume,
            volumeUnit = volumeUnit,
            createdAtEpochMillis = currentTimeMillis(),
        )
        aquariumDao.create(aquarium.toEntity())
        return aquarium
    }
}

private fun AquariumEntity.toDomain() = Aquarium(
    id = id,
    name = name,
    volume = volume,
    volumeUnit = VolumeUnit.fromStorageValue(volumeUnit),
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun Aquarium.toEntity() = AquariumEntity(
    id = id,
    name = name,
    volume = volume,
    volumeUnit = volumeUnit.storageValue,
    createdAtEpochMillis = createdAtEpochMillis,
)
