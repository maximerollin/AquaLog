package com.maximerollin.aqualog.shared

data class Session(
    val id: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
)

data class Measurement(
    val id: String,
    val sessionId: String,
    val parameterDefinitionId: String,
    val value: Double,
)

enum class MaintenanceActionType(val storageValue: String) {
    WATER_CHANGE("water_change"),
    FERTILIZATION("fertilization"),
    FILTER_MAINTENANCE("filter_maintenance"),
    VACUUM("vacuum"),
    GLASS_CLEANING("glass_cleaning"),
    PRUNING("pruning");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

data class MaintenanceAction(
    val id: String,
    val sessionId: String,
    val type: MaintenanceActionType,
    val quantity: Double?,
    val unit: String?,
    val product: String?,
)

enum class EventType(val storageValue: String) {
    OBSERVATION("observation"),
    INCIDENT("incident");

    companion object {
        fun fromStorageValue(value: String) = entries.first { it.storageValue == value }
    }
}

data class SessionEvent(
    val id: String,
    val sessionId: String,
    val type: EventType,
    val note: String,
)

data class RecordedSession(
    val session: Session,
    val measurements: List<Measurement>,
    val maintenanceActions: List<MaintenanceAction>,
    val events: List<SessionEvent>,
)

data class MaintenanceActionInput(
    val type: MaintenanceActionType,
    val quantity: String = "",
    val unit: String = "",
    val product: String = "",
)

data class RapidSessionInput(
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val idempotencyKey: String,
    val measurementInputs: Map<String, String> = emptyMap(),
    val maintenanceActions: List<MaintenanceActionInput> = emptyList(),
    val observation: String = "",
    val incident: String = "",
)

data class SessionEditInput(
    val sessionId: String,
    val aquariumId: String,
    val occurredAtEpochMillis: Long,
    val measurementInputs: Map<String, String> = emptyMap(),
    val maintenanceActions: List<MaintenanceActionInput> = emptyList(),
    val observation: String = "",
    val incident: String = "",
)

data class RapidSessionContext(
    val activeParameters: List<ParameterDefinition>,
    val measurementInputs: Map<String, String>,
    val lastMeasurements: Map<String, Double>,
    val lastMaintenanceActions: Map<MaintenanceActionType, MaintenanceAction>,
)

data class MeasurementFeedback(
    val value: Double?,
    val isValid: Boolean,
    val isOutsideIndicativeRange: Boolean,
)

fun evaluateMeasurement(input: String, definition: ParameterDefinition): MeasurementFeedback {
    if (input.isBlank()) return MeasurementFeedback(null, isValid = true, isOutsideIndicativeRange = false)
    val value = input.normalizedDecimalOrNull()
        ?: return MeasurementFeedback(null, isValid = false, isOutsideIndicativeRange = false)
    val outside = definition.indicativeMinimum?.let { value < it } == true ||
        definition.indicativeMaximum?.let { value > it } == true
    return MeasurementFeedback(value, isValid = value.isFinite(), isOutsideIndicativeRange = outside)
}

internal fun String.normalizedDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()
