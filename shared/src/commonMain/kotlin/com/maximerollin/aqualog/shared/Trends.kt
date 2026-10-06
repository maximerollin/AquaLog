package com.maximerollin.aqualog.shared

enum class TrendPeriod(val days: Int) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

data class TrendPoint(
    val sessionId: String,
    val occurredAtEpochMillis: Long,
    val value: Double,
)

data class ParameterTrendSeries(
    val definition: ParameterDefinition,
    val points: List<TrendPoint>,
)

data class TrendEvent(
    val type: EventType,
    val note: String,
)

data class TrendEventMarker(
    val sessionId: String,
    val occurredAtEpochMillis: Long,
    val maintenanceActionTypes: List<MaintenanceActionType>,
    val events: List<TrendEvent>,
)

data class TrendSnapshot(
    val aquarium: Aquarium,
    val period: TrendPeriod,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val series: List<ParameterTrendSeries>,
    val events: List<TrendEventMarker>,
)
