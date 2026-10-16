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

enum class TrendAccess { FREE, PRO }

data class TrendWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    init {
        require(startEpochMillis <= endEpochMillis) { "Trend period must be ordered" }
    }
}

data class AdvancedTrendSnapshot(
    val aquarium: Aquarium,
    val access: TrendAccess,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val series: List<ParameterTrendSeries>,
    val events: List<TrendEventMarker>,
)

data class ComparableTrendGroup(
    val dimension: TrendComparisonDimension,
    val canonicalUnit: String,
    val series: List<ParameterTrendSeries>,
)

enum class TrendComparisonDimension {
    TEMPERATURE,
    MASS_CONCENTRATION,
}

sealed interface InterventionSeriesComparison {
    val definitionId: String

    data class Available(
        override val definitionId: String,
        val unit: String,
        val beforeAverage: Double,
        val afterAverage: Double,
        val descriptiveDifference: Double,
    ) : InterventionSeriesComparison

    data class InsufficientData(
        override val definitionId: String,
        val unit: String,
    ) : InterventionSeriesComparison
}

data class InterventionComparison(
    val marker: TrendEventMarker,
    val beforeWindow: TrendWindow,
    val afterWindow: TrendWindow,
    val series: List<InterventionSeriesComparison>,
)

data class TrendTextAlternative(
    val period: LongRange,
    val series: List<String>,
    val annotations: List<String>,
    val interventionSummary: List<String>,
)

data class AdvancedTrendAnalysisResult(
    val snapshot: AdvancedTrendSnapshot,
    val comparisonGroups: List<ComparableTrendGroup>,
    val interventionComparison: InterventionComparison?,
    val textAlternative: TrendTextAlternative,
)

object AdvancedTrendAnalysis {
    fun create(
        snapshot: AdvancedTrendSnapshot,
        interventionSessionId: String? = null,
        comparisonWindowMillis: Long = 7L * 24L * 60L * 60L * 1_000L,
    ): AdvancedTrendAnalysisResult {
        require(comparisonWindowMillis > 0L) { "Comparison window must be positive" }
        val groups = snapshot.series
            .mapNotNull { series -> series.comparisonKey()?.let { it to series } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .map { (key, series) -> ComparableTrendGroup(key.first, key.second, series) }
            .sortedBy { it.dimension.name }
        val marker = interventionSessionId?.let { id -> snapshot.events.firstOrNull { it.sessionId == id } }
        val intervention = marker?.let {
            val before = TrendWindow(
                startEpochMillis = maxOf(snapshot.windowStartEpochMillis, it.occurredAtEpochMillis - comparisonWindowMillis),
                endEpochMillis = it.occurredAtEpochMillis,
            )
            val after = TrendWindow(
                startEpochMillis = it.occurredAtEpochMillis,
                endEpochMillis = minOf(snapshot.windowEndEpochMillis, it.occurredAtEpochMillis + comparisonWindowMillis),
            )
            InterventionComparison(
                marker = it,
                beforeWindow = before,
                afterWindow = after,
                series = snapshot.series.map { series -> series.compareAround(it.occurredAtEpochMillis, before, after) },
            )
        }
        return AdvancedTrendAnalysisResult(
            snapshot = snapshot,
            comparisonGroups = groups,
            interventionComparison = intervention,
            textAlternative = snapshot.textAlternative(intervention),
        )
    }
}

private fun ParameterTrendSeries.comparisonKey(): Pair<TrendComparisonDimension, String>? {
    val normalized = definition.unit.trim().lowercase()
    return when {
        definition.parameter == BuiltInParameter.TEMPERATURE && normalized in setOf("°c", "c", "celsius") ->
            TrendComparisonDimension.TEMPERATURE to "°C"
        definition.parameter in setOf(
            BuiltInParameter.AMMONIA,
            BuiltInParameter.NITRITE,
            BuiltInParameter.NITRATE,
            BuiltInParameter.PHOSPHATE,
        ) && normalized.replace(" ", "") == "mg/l" ->
            TrendComparisonDimension.MASS_CONCENTRATION to "mg/L"
        else -> null
    }
}

private fun ParameterTrendSeries.compareAround(
    interventionAt: Long,
    before: TrendWindow,
    after: TrendWindow,
): InterventionSeriesComparison {
    val beforeValues = points.filter {
        it.occurredAtEpochMillis in before.startEpochMillis until interventionAt
    }.map(TrendPoint::value)
    val afterValues = points.filter {
        it.occurredAtEpochMillis > interventionAt && it.occurredAtEpochMillis <= after.endEpochMillis
    }.map(TrendPoint::value)
    if (beforeValues.isEmpty() || afterValues.isEmpty()) {
        return InterventionSeriesComparison.InsufficientData(definition.id, definition.unit)
    }
    val beforeAverage = beforeValues.average()
    val afterAverage = afterValues.average()
    return InterventionSeriesComparison.Available(
        definitionId = definition.id,
        unit = definition.unit,
        beforeAverage = beforeAverage,
        afterAverage = afterAverage,
        descriptiveDifference = afterAverage - beforeAverage,
    )
}

private fun AdvancedTrendSnapshot.textAlternative(
    intervention: InterventionComparison?,
): TrendTextAlternative = TrendTextAlternative(
    period = windowStartEpochMillis..windowEndEpochMillis,
    series = series.map {
        "${it.definition.parameter.readableName()} · ${it.definition.unit} · ${it.points.size} values"
    },
    annotations = events.map { marker ->
        buildList {
            addAll(marker.maintenanceActionTypes.map(MaintenanceActionType::readableName))
            addAll(marker.events.map { "${it.type.readableName()}: ${it.note}" })
        }.joinToString(" · ")
    },
    interventionSummary = intervention?.series?.map { comparison ->
        when (comparison) {
            is InterventionSeriesComparison.Available -> {
                val name = series.first { it.definition.id == comparison.definitionId }.definition.parameter.readableName()
                "$name changed descriptively from ${comparison.beforeAverage} to ${comparison.afterAverage} ${comparison.unit}"
            }
            is InterventionSeriesComparison.InsufficientData -> {
                val name = series.first { it.definition.id == comparison.definitionId }.definition.parameter.readableName()
                "$name · insufficient data before or after the intervention"
            }
        }
    }.orEmpty(),
)

private fun BuiltInParameter.readableName(): String = storageValue
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun MaintenanceActionType.readableName(): String = storageValue
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun EventType.readableName(): String = storageValue
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
