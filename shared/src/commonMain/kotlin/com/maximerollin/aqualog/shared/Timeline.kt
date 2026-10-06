package com.maximerollin.aqualog.shared

data class TimelineFilter(
    val sinceEpochMillisInclusive: Long? = null,
    val aquariumId: String? = null,
    val parameterDefinitionId: String? = null,
    val maintenanceActionType: MaintenanceActionType? = null,
)

data class TimelineMeasurement(
    val measurement: Measurement,
    val definition: ParameterDefinition,
)

/**
 * One Chronology row per Session. Its child content remains grouped here so a
 * Measurement, maintenance Action or Event is never rendered twice.
 */
data class TimelineSession(
    val aquarium: Aquarium,
    val session: Session,
    val measurements: List<TimelineMeasurement>,
    val maintenanceActions: List<MaintenanceAction>,
    val events: List<SessionEvent>,
)
