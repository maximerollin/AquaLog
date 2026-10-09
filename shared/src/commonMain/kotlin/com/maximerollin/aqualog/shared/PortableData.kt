package com.maximerollin.aqualog.shared

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PORTABLE_DATA_FORMAT_VERSION = 1
const val PORTABLE_DATA_DUPLICATE_STRATEGY = "replace_local_portable_data"

enum class PortableDuplicateStrategy {
    REPLACE_LOCAL_PORTABLE_DATA,
}

enum class PortableImportRejection {
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    INVALID_DATA,
    IMPORT_FAILED,
}

sealed interface PortableImportResult {
    data class Imported(
        val duplicateStrategy: PortableDuplicateStrategy,
    ) : PortableImportResult

    data class Rejected(
        val reason: PortableImportRejection,
        val explanation: String,
    ) : PortableImportResult
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PortableDataDocument(
    @Required
    val formatVersion: Int = PORTABLE_DATA_FORMAT_VERSION,
    @Required
    val duplicateStrategy: String = PORTABLE_DATA_DUPLICATE_STRATEGY,
    @Required
    val aquariums: List<PortableAquarium> = emptyList(),
    @Required
    val parameterDefinitions: List<PortableParameterDefinition> = emptyList(),
    @Required
    val sessions: List<PortableSession> = emptyList(),
    @Required
    val measurements: List<PortableMeasurement> = emptyList(),
    @Required
    val maintenanceActions: List<PortableMaintenanceAction> = emptyList(),
    @Required
    val events: List<PortableEvent> = emptyList(),
    @Required
    val tasks: List<PortableTask> = emptyList(),
    @Required
    val taskOccurrences: List<PortableTaskOccurrence> = emptyList(),
)

@Serializable
data class PortableAquarium(
    val id: String,
    val name: String,
    val volume: Double,
    val volumeUnit: String,
    val createdAtEpochMillis: Long,
    val profile: String,
)

@Serializable
data class PortableParameterDefinition(
    val id: String,
    val aquariumId: String,
    val parameter: String,
    val isActive: Boolean,
    val position: Int,
    val unit: String,
    val precision: Int,
    val indicativeMinimum: Double?,
    val indicativeMaximum: Double?,
)

@Serializable
data class PortableSession(
    val id: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val idempotencyKey: String,
)

@Serializable
data class PortableMeasurement(
    val id: String,
    val sessionId: String,
    val parameterDefinitionId: String,
    val value: Double,
)

@Serializable
data class PortableMaintenanceAction(
    val id: String,
    val sessionId: String,
    val type: String,
    val quantity: Double?,
    val unit: String?,
    val product: String?,
)

@Serializable
data class PortableEvent(
    val id: String,
    val sessionId: String,
    val type: String,
    val note: String,
)

@Serializable
data class PortableTask(
    val id: String,
    val aquariumId: String,
    val title: String,
    val recurrence: String,
    val monthlyAnchorDay: Int,
)

@Serializable
data class PortableTaskOccurrence(
    val id: String,
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

class PortableDataService(
    database: AquaLogDatabase,
) {
    private val portableDataDao = database.portableDataDao()

    suspend fun export(): String = PORTABLE_JSON.encodeToString(portableDataDao.snapshot().toDocument())

    suspend fun importData(payload: String): PortableImportResult {
        val document = when (val decoded = decodeAndMigrate(payload)) {
            is PortableDecodeResult.Accepted -> decoded.document
            is PortableDecodeResult.Rejected -> return PortableImportResult.Rejected(
                reason = decoded.reason,
                explanation = decoded.explanation,
            )
        }
        document.validationError()?.let { explanation ->
            return PortableImportResult.Rejected(
                reason = PortableImportRejection.INVALID_DATA,
                explanation = explanation,
            )
        }
        return try {
            portableDataDao.replaceAll(document.toSnapshot())
            PortableImportResult.Imported(PortableDuplicateStrategy.REPLACE_LOCAL_PORTABLE_DATA)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PortableImportResult.Rejected(
                reason = PortableImportRejection.IMPORT_FAILED,
                explanation = "The local copy could not be replaced; no imported data was kept.",
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private val PORTABLE_JSON = Json {
    encodeDefaults = true
    explicitNulls = true
    prettyPrint = true
    prettyPrintIndent = "    "
}

private sealed interface PortableDecodeResult {
    data class Accepted(val document: PortableDataDocument) : PortableDecodeResult
    data class Rejected(
        val reason: PortableImportRejection,
        val explanation: String,
    ) : PortableDecodeResult
}

private fun decodeAndMigrate(payload: String): PortableDecodeResult {
    val root = try {
        PORTABLE_JSON.parseToJsonElement(payload).jsonObject
    } catch (_: Exception) {
        return PortableDecodeResult.Rejected(
            PortableImportRejection.INVALID_FORMAT,
            "The selected file is not a complete AquaLog JSON export.",
        )
    }
    val version = root["formatVersion"]?.jsonPrimitive?.intOrNull
        ?: return PortableDecodeResult.Rejected(
            PortableImportRejection.INVALID_FORMAT,
            "The selected file has no numeric formatVersion.",
        )
    if (version != PORTABLE_DATA_FORMAT_VERSION) {
        return PortableDecodeResult.Rejected(
            PortableImportRejection.UNSUPPORTED_VERSION,
            "AquaLog export version $version is not supported by this app.",
        )
    }
    val document = try {
        when (version) {
            1 -> PORTABLE_JSON.decodeFromString<PortableDataDocument>(payload)
            else -> error("Version was checked before migration")
        }
    } catch (_: Exception) {
        return PortableDecodeResult.Rejected(
            PortableImportRejection.INVALID_FORMAT,
            "The selected file does not match the AquaLog version $version schema.",
        )
    }
    return PortableDecodeResult.Accepted(document)
}

private fun PortableDataDocument.validationError(): String? {
    if (duplicateStrategy != PORTABLE_DATA_DUPLICATE_STRATEGY) {
        return "The export uses an unsupported duplicate strategy."
    }
    val idLists = listOf(
        "Aquarium" to aquariums.map(PortableAquarium::id),
        "Parameter" to parameterDefinitions.map(PortableParameterDefinition::id),
        "Session" to sessions.map(PortableSession::id),
        "Measurement" to measurements.map(PortableMeasurement::id),
        "Action" to maintenanceActions.map(PortableMaintenanceAction::id),
        "Event" to events.map(PortableEvent::id),
        "Task" to tasks.map(PortableTask::id),
        "Occurrence" to taskOccurrences.map(PortableTaskOccurrence::id),
    )
    idLists.firstOrNull { (_, ids) -> ids.any(String::isBlank) || ids.distinct().size != ids.size }
        ?.let { (kind) -> return "$kind identifiers must be non-empty and unique." }

    if (aquariums.any {
            it.name.isBlank() || !it.volume.isFinite() || it.volume <= 0.0 ||
                runCatching { VolumeUnit.fromStorageValue(it.volumeUnit) }.isFailure ||
                runCatching { AquariumProfile.fromStorageValue(it.profile) }.isFailure
        }
    ) {
        return "An Aquarium record is invalid."
    }
    val aquariumsById = aquariums.associateBy(PortableAquarium::id)
    if (parameterDefinitions.any {
            it.aquariumId !in aquariumsById || it.unit.isBlank() || it.position < 0 || it.precision !in 0..3 ||
                it.indicativeMinimum?.isFinite() == false || it.indicativeMaximum?.isFinite() == false ||
                it.indicativeMinimum != null && it.indicativeMaximum != null &&
                it.indicativeMinimum > it.indicativeMaximum ||
                runCatching { BuiltInParameter.fromStorageValue(it.parameter) }.isFailure
        }
    ) {
        return "A Parameter record is invalid or references a missing Aquarium."
    }
    if (parameterDefinitions.hasDuplicateBy { it.aquariumId to it.parameter }) {
        return "Each built-in Parameter may appear only once per Aquarium."
    }
    if (sessions.any {
            it.aquariumId !in aquariumsById || it.idempotencyKey.isBlank()
        } || sessions.hasDuplicateBy(PortableSession::idempotencyKey)
    ) {
        return "A Session record is invalid, duplicated, or references a missing Aquarium."
    }
    val sessionsById = sessions.associateBy(PortableSession::id)
    val parametersById = parameterDefinitions.associateBy(PortableParameterDefinition::id)
    if (measurements.any { measurement ->
            val session = sessionsById[measurement.sessionId]
            val parameter = parametersById[measurement.parameterDefinitionId]
            !measurement.value.isFinite() || session == null || parameter == null ||
                session.aquariumId != parameter.aquariumId
        }
    ) {
        return "A Measurement record is invalid or has broken Session and Parameter relations."
    }
    if (maintenanceActions.any {
            it.sessionId !in sessionsById || it.quantity?.isFinite() == false ||
                runCatching { MaintenanceActionType.fromStorageValue(it.type) }.isFailure
        }
    ) {
        return "A maintenance Action record is invalid or references a missing Session."
    }
    if (events.any {
            it.sessionId !in sessionsById || it.note.isBlank() ||
                runCatching { EventType.fromStorageValue(it.type) }.isFailure
        }
    ) {
        return "An Event record is invalid or references a missing Session."
    }
    if (tasks.any {
            it.aquariumId !in aquariumsById || it.title.isBlank() || it.monthlyAnchorDay !in 1..31 ||
                runCatching { TaskRecurrence.fromStorageValue(it.recurrence) }.isFailure
        }
    ) {
        return "A Task record is invalid or references a missing Aquarium."
    }
    val tasksById = tasks.associateBy(PortableTask::id)
    if (taskOccurrences.any { occurrence ->
            val task = tasksById[occurrence.taskId]
            task == null || occurrence.aquariumId != task.aquariumId || occurrence.title.isBlank() ||
                occurrence.dueMinuteOfDay !in -1 until 24 * 60 || occurrence.timeZoneId.isBlank() ||
                runCatching { TaskDate(occurrence.dueYear, occurrence.dueMonth, occurrence.dueDay) }.isFailure ||
                occurrence.resolution?.let { runCatching { TaskResolution.fromStorageValue(it) }.isFailure } == true ||
                (occurrence.resolution == null) != (occurrence.resolvedAtEpochMillis == null)
        }
    ) {
        return "A Task Occurrence is invalid or has broken Task and Aquarium relations."
    }
    if (taskOccurrences.hasDuplicateBy {
            listOf(it.taskId, it.dueYear, it.dueMonth, it.dueDay, it.dueMinuteOfDay, it.timeZoneId)
        }
    ) {
        return "A Task Occurrence due date is duplicated."
    }
    return null
}

private fun <T, K> List<T>.hasDuplicateBy(selector: (T) -> K): Boolean {
    val values = map(selector)
    return values.distinct().size != values.size
}

private fun PortableDatabaseSnapshot.toDocument() = PortableDataDocument(
    aquariums = aquariums.map { it.toPortable() }.sortedBy(PortableAquarium::id),
    parameterDefinitions = parameterDefinitions.map { it.toPortable() }.sortedBy(PortableParameterDefinition::id),
    sessions = sessions.map { it.toPortable() }.sortedBy(PortableSession::id),
    measurements = measurements.map { it.toPortable() }.sortedBy(PortableMeasurement::id),
    maintenanceActions = maintenanceActions.map { it.toPortable() }.sortedBy(PortableMaintenanceAction::id),
    events = events.map { it.toPortable() }.sortedBy(PortableEvent::id),
    tasks = tasks.map { it.toPortable() }.sortedBy(PortableTask::id),
    taskOccurrences = taskOccurrences.map { it.toPortable() }.sortedBy(PortableTaskOccurrence::id),
)

private fun PortableDataDocument.toSnapshot() = PortableDatabaseSnapshot(
    aquariums = aquariums.map(PortableAquarium::toEntity),
    parameterDefinitions = parameterDefinitions.map(PortableParameterDefinition::toEntity),
    sessions = sessions.map(PortableSession::toEntity),
    measurements = measurements.map(PortableMeasurement::toEntity),
    maintenanceActions = maintenanceActions.map(PortableMaintenanceAction::toEntity),
    events = events.map(PortableEvent::toEntity),
    tasks = tasks.map(PortableTask::toEntity),
    taskOccurrences = taskOccurrences.map(PortableTaskOccurrence::toEntity),
)

private fun AquariumEntity.toPortable() = PortableAquarium(id, name, volume, volumeUnit, createdAtEpochMillis, profile)

private fun PortableAquarium.toEntity() = AquariumEntity(id, name, volume, volumeUnit, createdAtEpochMillis, profile)

private fun ParameterDefinitionEntity.toPortable() = PortableParameterDefinition(
    id,
    aquariumId,
    parameter,
    isActive,
    position,
    unit,
    precision,
    indicativeMinimum,
    indicativeMaximum,
)

private fun PortableParameterDefinition.toEntity() = ParameterDefinitionEntity(
    id,
    aquariumId,
    parameter,
    isActive,
    position,
    unit,
    precision,
    indicativeMinimum,
    indicativeMaximum,
)

private fun SessionEntity.toPortable() = PortableSession(
    id,
    aquariumId,
    occurredAtEpochMillis,
    createdAtEpochMillis,
    idempotencyKey,
)

private fun PortableSession.toEntity() = SessionEntity(
    id,
    aquariumId,
    occurredAtEpochMillis,
    createdAtEpochMillis,
    idempotencyKey,
)

private fun MeasurementEntity.toPortable() = PortableMeasurement(id, sessionId, parameterDefinitionId, value)

private fun PortableMeasurement.toEntity() = MeasurementEntity(id, sessionId, parameterDefinitionId, value)

private fun MaintenanceActionEntity.toPortable() = PortableMaintenanceAction(
    id,
    sessionId,
    type,
    quantity,
    unit,
    product,
)

private fun PortableMaintenanceAction.toEntity() = MaintenanceActionEntity(
    id,
    sessionId,
    type,
    quantity,
    unit,
    product,
)

private fun SessionEventEntity.toPortable() = PortableEvent(id, sessionId, type, note)

private fun PortableEvent.toEntity() = SessionEventEntity(id, sessionId, type, note)

private fun TaskEntity.toPortable() = PortableTask(id, aquariumId, title, recurrence, monthlyAnchorDay)

private fun PortableTask.toEntity() = TaskEntity(id, aquariumId, title, recurrence, monthlyAnchorDay)

private fun TaskOccurrenceEntity.toPortable() = PortableTaskOccurrence(
    id,
    taskId,
    aquariumId,
    title,
    dueYear,
    dueMonth,
    dueDay,
    dueMinuteOfDay,
    timeZoneId,
    resolution,
    resolvedAtEpochMillis,
)

private fun PortableTaskOccurrence.toEntity() = TaskOccurrenceEntity(
    id,
    taskId,
    aquariumId,
    title,
    dueYear,
    dueMonth,
    dueDay,
    dueMinuteOfDay,
    timeZoneId,
    resolution,
    resolvedAtEpochMillis,
)
