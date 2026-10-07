package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    suspend fun loadRapidSessionContext(aquariumId: String): RapidSessionContext

    suspend fun saveRapidSession(input: RapidSessionInput): RecordedSession

    fun observeLatestSession(aquariumId: String): Flow<RecordedSession?>

    fun observeSession(sessionId: String): Flow<RecordedSession?>

    suspend fun updateSession(input: SessionEditInput): RecordedSession

    suspend fun deleteSession(sessionId: String)

    fun observeTimeline(filter: TimelineFilter): Flow<List<TimelineSession>>

    fun observeFreeTrends(aquariumId: String, period: TrendPeriod): Flow<TrendSnapshot>
}

class RoomAquariumRepository(
    database: AquaLogDatabase,
    private val generateId: () -> String,
    private val currentTimeMillis: () -> Long,
) : AquariumRepository, AccountLocalDataSource {
    private val aquariumDao = database.aquariumDao()
    private val sessionDao = database.sessionDao()
    private val accountDao = database.accountDao()

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

    override suspend fun loadRapidSessionContext(aquariumId: String): RapidSessionContext {
        val setup = requireNotNull(aquariumDao.getSetup(aquariumId)) { "Aquarium does not exist" }.toDomain()
        val activeParameters = setup.parameters.filter(ParameterDefinition::isActive)
            .sortedBy(ParameterDefinition::position)
        val lastMeasurements = sessionDao.measurementsForAquarium(aquariumId)
            .distinctBy(MeasurementEntity::parameterDefinitionId)
            .associate { it.parameterDefinitionId to it.value }
        val lastActions = sessionDao.maintenanceActionsForAquarium(aquariumId)
            .map(MaintenanceActionEntity::toDomain)
            .distinctBy(MaintenanceAction::type)
            .associateBy(MaintenanceAction::type)
        return RapidSessionContext(
            activeParameters = activeParameters,
            measurementInputs = activeParameters.associate { it.id to "" },
            lastMeasurements = lastMeasurements,
            lastMaintenanceActions = lastActions,
        )
    }

    override suspend fun saveRapidSession(input: RapidSessionInput): RecordedSession {
        require(input.idempotencyKey.isNotBlank()) { "A Session interaction requires an idempotency key" }
        val setup = requireNotNull(aquariumDao.getSetup(input.aquariumId)) { "Aquarium does not exist" }.toDomain()
        val activeParameters = setup.parameters.filter(ParameterDefinition::isActive).associateBy(ParameterDefinition::id)
        val session = Session(
            id = generateId(),
            aquariumId = input.aquariumId,
            occurredAtEpochMillis = input.occurredAtEpochMillis,
            createdAtEpochMillis = currentTimeMillis(),
        )
        val measurements = input.measurementInputs.mapNotNull { (parameterId, rawValue) ->
            if (rawValue.isBlank()) return@mapNotNull null
            val definition = requireNotNull(activeParameters[parameterId]) { "Measure must target an active Parameter" }
            val feedback = evaluateMeasurement(rawValue, definition)
            require(feedback.isValid) { "Measure must be a decimal value" }
            Measurement(
                id = generateId(),
                sessionId = session.id,
                parameterDefinitionId = parameterId,
                value = requireNotNull(feedback.value),
            )
        }
        val actions = input.maintenanceActions.map { draft ->
            val quantity = if (draft.quantity.isBlank()) null else draft.quantity.normalizedDecimalOrNull()
            require(draft.quantity.isBlank() || quantity?.isFinite() == true) { "Action quantity must be a decimal value" }
            MaintenanceAction(
                id = generateId(),
                sessionId = session.id,
                type = draft.type,
                quantity = quantity,
                unit = draft.unit.trim().ifBlank { null },
                product = draft.product.trim().ifBlank { null },
            )
        }
        val events = buildList {
            input.observation.trim().takeIf(String::isNotEmpty)?.let { note ->
                add(SessionEvent(generateId(), session.id, EventType.OBSERVATION, note))
            }
            input.incident.trim().takeIf(String::isNotEmpty)?.let { note ->
                add(SessionEvent(generateId(), session.id, EventType.INCIDENT, note))
            }
        }
        require(measurements.isNotEmpty() || actions.isNotEmpty() || events.isNotEmpty()) {
            "An empty Session cannot be recorded"
        }
        return sessionDao.create(
            session = session.toEntity(input.idempotencyKey),
            measurements = measurements.map(Measurement::toEntity),
            actions = actions.map(MaintenanceAction::toEntity),
            events = events.map(SessionEvent::toEntity),
        ).toDomain()
    }

    override fun observeLatestSession(aquariumId: String): Flow<RecordedSession?> =
        sessionDao.observeLatest(aquariumId).map { it?.toDomain() }

    override fun observeSession(sessionId: String): Flow<RecordedSession?> =
        sessionDao.observeById(sessionId).map { it?.toDomain() }

    override suspend fun updateSession(input: SessionEditInput): RecordedSession {
        val setup = requireNotNull(aquariumDao.getSetup(input.aquariumId)) { "Aquarium does not exist" }.toDomain()
        val activeParameters = setup.parameters.filter(ParameterDefinition::isActive).associateBy(ParameterDefinition::id)
        val measurements = input.measurementInputs.mapNotNull { (parameterId, rawValue) ->
            if (rawValue.isBlank()) return@mapNotNull null
            val definition = requireNotNull(activeParameters[parameterId]) { "Measure must target an active Parameter" }
            val feedback = evaluateMeasurement(rawValue, definition)
            require(feedback.isValid) { "Measure must be a decimal value" }
            Measurement(
                id = generateId(),
                sessionId = input.sessionId,
                parameterDefinitionId = parameterId,
                value = requireNotNull(feedback.value),
            )
        }
        val actions = input.maintenanceActions.map { draft ->
            val quantity = if (draft.quantity.isBlank()) null else draft.quantity.normalizedDecimalOrNull()
            require(draft.quantity.isBlank() || quantity?.isFinite() == true) { "Action quantity must be a decimal value" }
            MaintenanceAction(
                id = generateId(),
                sessionId = input.sessionId,
                type = draft.type,
                quantity = quantity,
                unit = draft.unit.trim().ifBlank { null },
                product = draft.product.trim().ifBlank { null },
            )
        }
        val events = buildList {
            input.observation.trim().takeIf(String::isNotEmpty)?.let { note ->
                add(SessionEvent(generateId(), input.sessionId, EventType.OBSERVATION, note))
            }
            input.incident.trim().takeIf(String::isNotEmpty)?.let { note ->
                add(SessionEvent(generateId(), input.sessionId, EventType.INCIDENT, note))
            }
        }
        require(measurements.isNotEmpty() || actions.isNotEmpty() || events.isNotEmpty()) {
            "An empty Session cannot be recorded"
        }
        return sessionDao.update(
            sessionId = input.sessionId,
            aquariumId = input.aquariumId,
            occurredAtEpochMillis = input.occurredAtEpochMillis,
            measurements = measurements.map(Measurement::toEntity),
            actions = actions.map(MaintenanceAction::toEntity),
            events = events.map(SessionEvent::toEntity),
        ).toDomain()
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.delete(sessionId)
    }

    override fun observeTimeline(filter: TimelineFilter): Flow<List<TimelineSession>> =
        combine(
            aquariumDao.observeAllSetups(),
            sessionDao.observeAll(),
        ) { setupEntities, recordedEntities ->
            val setups = setupEntities.map(AquariumSetupEntity::toDomain)
                .associateBy { it.aquarium.id }
            recordedEntities.mapNotNull { recordedEntity ->
                val recorded = recordedEntity.toDomain()
                val setup = setups[recorded.session.aquariumId] ?: return@mapNotNull null
                TimelineSession(
                    aquarium = setup.aquarium,
                    session = recorded.session,
                    measurements = recorded.measurements.mapNotNull { measurement ->
                        setup.parameters.firstOrNull { it.id == measurement.parameterDefinitionId }
                            ?.let { TimelineMeasurement(measurement, it) }
                    },
                    maintenanceActions = recorded.maintenanceActions,
                    events = recorded.events,
                )
            }.filter { item ->
                (filter.sinceEpochMillisInclusive == null ||
                    item.session.occurredAtEpochMillis >= filter.sinceEpochMillisInclusive) &&
                    (filter.aquariumId == null || item.aquarium.id == filter.aquariumId) &&
                    (filter.parameterDefinitionId == null ||
                        item.measurements.any { it.definition.id == filter.parameterDefinitionId }) &&
                    (filter.maintenanceActionType == null ||
                        item.maintenanceActions.any { it.type == filter.maintenanceActionType })
            }
        }

    override fun observeFreeTrends(aquariumId: String, period: TrendPeriod): Flow<TrendSnapshot> =
        currentTimeMillis().let { now ->
            combine(
                aquariumDao.observeAllSetups(),
                sessionDao.observeForAquariumBetween(
                    aquariumId = aquariumId,
                    sinceEpochMillisInclusive = now - period.days * DAY_MILLIS,
                    untilEpochMillisInclusive = now,
                ),
            ) { setupEntities, recordedEntities ->
                val setup = requireNotNull(
                    setupEntities.firstOrNull { it.aquarium.id == aquariumId },
                ) { "Aquarium does not exist" }.toDomain()
                TrendSnapshot(
                    aquarium = setup.aquarium,
                    period = period,
                    windowStartEpochMillis = now - period.days * DAY_MILLIS,
                    windowEndEpochMillis = now,
                    series = setup.parameters.filter(ParameterDefinition::isActive).map { definition ->
                        ParameterTrendSeries(
                            definition = definition,
                            points = recordedEntities.mapNotNull { recorded ->
                                recorded.measurements.firstOrNull {
                                    it.parameterDefinitionId == definition.id
                                }?.let { measurement ->
                                    TrendPoint(
                                        sessionId = recorded.session.id,
                                        occurredAtEpochMillis = recorded.session.occurredAtEpochMillis,
                                        value = measurement.value,
                                    )
                                }
                            }.sortedBy(TrendPoint::occurredAtEpochMillis),
                        )
                    },
                    events = recordedEntities.mapNotNull { recorded ->
                        if (recorded.maintenanceActions.isEmpty() && recorded.events.isEmpty()) {
                            return@mapNotNull null
                        }
                        TrendEventMarker(
                            sessionId = recorded.session.id,
                            occurredAtEpochMillis = recorded.session.occurredAtEpochMillis,
                            maintenanceActionTypes = recorded.maintenanceActions
                                .map { MaintenanceActionType.fromStorageValue(it.type) },
                            events = recorded.events.map {
                                TrendEvent(EventType.fromStorageValue(it.type), it.note)
                            },
                        )
                    }.sortedBy(TrendEventMarker::occurredAtEpochMillis),
                )
            }
        }

    suspend fun sessionCount(aquariumId: String): Int = sessionDao.count(aquariumId)

    override suspend fun hasRecordedSession(): Boolean = sessionDao.countAll() > 0

    override suspend fun wasAccountInvitationOffered(): Boolean =
        accountDao.wasInvitationOffered() == true

    override suspend fun markAccountInvitationOffered() {
        accountDao.upsertInvitation(AccountInvitationEntity(wasOffered = true))
    }

    override suspend fun initialAccountCopy() = InitialAccountCopy(
        aquariums = aquariumDao.allAquariums().map(AquariumEntity::toDomain),
        parameterDefinitions = aquariumDao.allParameterDefinitions().map(ParameterDefinitionEntity::toDomain),
        sessions = sessionDao.allSessions().map(SessionEntity::toDomain),
        measurements = sessionDao.allMeasurements().map(MeasurementEntity::toDomain),
        maintenanceActions = sessionDao.allMaintenanceActions().map(MaintenanceActionEntity::toDomain),
        events = sessionDao.allEvents().map(SessionEventEntity::toDomain),
    )

    override suspend fun accountMigrationState(): AccountMigrationState? = accountDao.get()?.let {
        AccountMigrationState(it.accountId, InitialMigrationStatus.fromStorageValue(it.migrationStatus))
    }

    override suspend fun beginAccountMigration(accountId: String) {
        val current = accountDao.get()
        if (current?.accountId == accountId && current.migrationStatus == InitialMigrationStatus.COMPLETE.storageValue) return
        accountDao.upsert(AccountStateEntity(accountId = accountId, migrationStatus = InitialMigrationStatus.PENDING.storageValue))
    }

    override suspend fun completeAccountMigration(accountId: String) {
        accountDao.upsert(AccountStateEntity(accountId = accountId, migrationStatus = InitialMigrationStatus.COMPLETE.storageValue))
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

private fun Session.toEntity(idempotencyKey: String) = SessionEntity(
    id = id,
    aquariumId = aquariumId,
    occurredAtEpochMillis = occurredAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    idempotencyKey = idempotencyKey,
)

private fun SessionEntity.toDomain() = Session(id, aquariumId, occurredAtEpochMillis, createdAtEpochMillis)

private fun Measurement.toEntity() = MeasurementEntity(id, sessionId, parameterDefinitionId, value)

private fun MeasurementEntity.toDomain() = Measurement(id, sessionId, parameterDefinitionId, value)

private fun MaintenanceAction.toEntity() = MaintenanceActionEntity(
    id,
    sessionId,
    type.storageValue,
    quantity,
    unit,
    product,
)

private fun MaintenanceActionEntity.toDomain() = MaintenanceAction(
    id,
    sessionId,
    MaintenanceActionType.fromStorageValue(type),
    quantity,
    unit,
    product,
)

private fun SessionEvent.toEntity() = SessionEventEntity(id, sessionId, type.storageValue, note)

private fun SessionEventEntity.toDomain() = SessionEvent(id, sessionId, EventType.fromStorageValue(type), note)

private fun RecordedSessionEntity.toDomain() = RecordedSession(
    session = session.toDomain(),
    measurements = measurements.map(MeasurementEntity::toDomain),
    maintenanceActions = maintenanceActions.map(MaintenanceActionEntity::toDomain),
    events = events.map(SessionEventEntity::toDomain),
)

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
