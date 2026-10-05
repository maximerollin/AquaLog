package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AquariumRepository {
    fun observeCurrentAquarium(): Flow<Aquarium?>

    fun observeCurrentSetup(): Flow<AquariumSetup?>

    suspend fun createAquarium(
        name: String,
        volume: Double,
        volumeUnit: VolumeUnit,
    ): Aquarium

    suspend fun createConfiguredAquarium(
        name: String,
        volume: Double,
        volumeUnit: VolumeUnit,
        profile: AquariumProfile,
        parameters: List<ParameterDefinitionDraft>,
    ): AquariumSetup
}

class RoomAquariumRepository(
    database: AquaLogDatabase,
    private val generateId: () -> String,
    private val currentTimeMillis: () -> Long,
) : AquariumRepository {
    private val aquariumDao = database.aquariumDao()

    override fun observeCurrentAquarium(): Flow<Aquarium?> =
        aquariumDao.observeCurrent().map { it?.toDomain() }

    override fun observeCurrentSetup(): Flow<AquariumSetup?> =
        aquariumDao.observeCurrentSetup().map { it?.toDomain() }

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

    override suspend fun createConfiguredAquarium(
        name: String,
        volume: Double,
        volumeUnit: VolumeUnit,
        profile: AquariumProfile,
        parameters: List<ParameterDefinitionDraft>,
    ): AquariumSetup {
        require(name.isNotBlank()) { "Aquarium name must not be blank" }
        require(volume > 0.0 && volume.isFinite()) { "Aquarium volume must be positive" }
        require(
            parameters.size == BuiltInParameter.entries.size &&
                parameters.map(ParameterDefinitionDraft::parameter).toSet() == BuiltInParameter.entries.toSet(),
        ) {
            "Every built-in Parameter must be configured exactly once"
        }
        require(parameters.map(ParameterDefinitionDraft::position).toSet() == parameters.indices.toSet()) {
            "Parameter positions must be contiguous and unique"
        }
        require(parameters.any(ParameterDefinitionDraft::isActive)) {
            "At least one Parameter must be active"
        }
        parameters.forEach { parameter ->
            require(parameter.unit.isNotBlank()) { "Parameter unit must not be blank" }
            require(parameter.precision in 0..3) { "Parameter precision must be between zero and three" }
            require(
                parameter.indicativeMinimum == null ||
                    parameter.indicativeMaximum == null ||
                    parameter.indicativeMinimum <= parameter.indicativeMaximum,
            ) { "Parameter indicative range must be ordered" }
        }

        val aquarium = Aquarium(
            id = generateId(),
            name = name.trim(),
            volume = volume,
            volumeUnit = volumeUnit,
            createdAtEpochMillis = currentTimeMillis(),
            profile = profile,
        )
        val definitions = parameters.sortedBy(ParameterDefinitionDraft::position).map { draft ->
            ParameterDefinition(
                id = generateId(),
                aquariumId = aquarium.id,
                parameter = draft.parameter,
                isActive = draft.isActive,
                position = draft.position,
                unit = draft.unit.trim(),
                precision = draft.precision,
                indicativeMinimum = draft.indicativeMinimum,
                indicativeMaximum = draft.indicativeMaximum,
            )
        }
        aquariumDao.createConfigured(
            aquarium = aquarium.toEntity(),
            parameters = definitions.map(ParameterDefinition::toEntity),
        )
        return AquariumSetup(aquarium, definitions)
    }
}

private fun AquariumEntity.toDomain() = Aquarium(
    id = id,
    name = name,
    volume = volume,
    volumeUnit = VolumeUnit.fromStorageValue(volumeUnit),
    createdAtEpochMillis = createdAtEpochMillis,
    profile = AquariumProfile.fromStorageValue(profile),
)

private fun Aquarium.toEntity() = AquariumEntity(
    id = id,
    name = name,
    volume = volume,
    volumeUnit = volumeUnit.storageValue,
    createdAtEpochMillis = createdAtEpochMillis,
    profile = profile.storageValue,
)

private fun AquariumSetupEntity.toDomain() = AquariumSetup(
    aquarium = aquarium.toDomain(),
    parameters = parameters.map(ParameterDefinitionEntity::toDomain).sortedBy(ParameterDefinition::position),
)

private fun ParameterDefinitionEntity.toDomain() = ParameterDefinition(
    id = id,
    aquariumId = aquariumId,
    parameter = BuiltInParameter.fromStorageValue(parameter),
    isActive = isActive,
    position = position,
    unit = unit,
    precision = precision,
    indicativeMinimum = indicativeMinimum,
    indicativeMaximum = indicativeMaximum,
)

private fun ParameterDefinition.toEntity() = ParameterDefinitionEntity(
    id = id,
    aquariumId = aquariumId,
    parameter = parameter.storageValue,
    isActive = isActive,
    position = position,
    unit = unit,
    precision = precision,
    indicativeMinimum = indicativeMinimum,
    indicativeMaximum = indicativeMaximum,
)
