package com.maximerollin.aqualog.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdvancedTrendAnalysisTest {
    @Test
    fun `known compatible units form canonical comparison groups while incompatible units stay separate`() {
        val snapshot = advancedSnapshot(
            series = listOf(
                series("nitrate", BuiltInParameter.NITRATE, "mg/L", 10.0),
                series("phosphate", BuiltInParameter.PHOSPHATE, "mg/l", 0.4),
                series("ph", BuiltInParameter.PH, "pH", 7.2),
                series("tds", BuiltInParameter.TDS, "ppm", 120.0),
            ),
        )

        val result = AdvancedTrendAnalysis.create(snapshot)

        assertEquals(listOf(listOf("nitrate", "phosphate")), result.comparisonGroups.map { group ->
            group.series.map { it.definition.id }
        })
        assertFalse(result.comparisonGroups.flattenedIds().contains("ph"))
        assertFalse(result.comparisonGroups.flattenedIds().contains("tds"))
        assertEquals("mg/L", result.comparisonGroups.single().canonicalUnit)
    }

    @Test
    fun `before and after uses deterministic windows and reports incomplete data without a conclusion`() {
        val intervention = TrendEventMarker(
            sessionId = "intervention",
            occurredAtEpochMillis = 10 * DAY,
            maintenanceActionTypes = listOf(MaintenanceActionType.WATER_CHANGE),
            events = emptyList(),
        )
        val temperature = series(
            "temperature",
            BuiltInParameter.TEMPERATURE,
            "°C",
            24.0,
            TrendPoint("before", 8 * DAY, 23.0),
            TrendPoint("after", 12 * DAY, 25.0),
        )
        val incomplete = series(
            "nitrate",
            BuiltInParameter.NITRATE,
            "mg/L",
            10.0,
            TrendPoint("only-before", 9 * DAY, 12.0),
        )

        val result = AdvancedTrendAnalysis.create(
            advancedSnapshot(series = listOf(temperature, incomplete), events = listOf(intervention)),
            interventionSessionId = intervention.sessionId,
            comparisonWindowMillis = 3 * DAY,
        )

        val comparison = requireNotNull(result.interventionComparison)
        val complete = comparison.series.first { it.definitionId == "temperature" }
        assertIs<InterventionSeriesComparison.Available>(complete)
        assertEquals(23.0, complete.beforeAverage)
        assertEquals(25.0, complete.afterAverage)
        assertEquals(2.0, complete.descriptiveDifference)
        assertIs<InterventionSeriesComparison.InsufficientData>(
            comparison.series.first { it.definitionId == "nitrate" },
        )
        assertIs<InterventionSeriesComparison.InsufficientData>(
            result.textAlternative.interventionComparison!!.series.first { it.definitionId == "nitrate" },
        )
    }

    @Test
    fun `text alternative carries the same period series units annotations and descriptive comparison`() {
        val marker = TrendEventMarker(
            sessionId = "maintenance",
            occurredAtEpochMillis = 10 * DAY,
            maintenanceActionTypes = listOf(MaintenanceActionType.FILTER_MAINTENANCE),
            events = listOf(TrendEvent(EventType.OBSERVATION, "Clear water")),
        )
        val result = AdvancedTrendAnalysis.create(
            advancedSnapshot(
                series = listOf(
                    series(
                        "temperature",
                        BuiltInParameter.TEMPERATURE,
                        "°C",
                        24.0,
                        TrendPoint("before", 9 * DAY, 23.0),
                        TrendPoint("after", 11 * DAY, 24.0),
                    ),
                ),
                events = listOf(marker),
            ),
            interventionSessionId = marker.sessionId,
            comparisonWindowMillis = 2 * DAY,
        )

        assertEquals(0L..20 * DAY, result.textAlternative.period)
        val textSeries = result.textAlternative.series.single()
        assertEquals("°C", textSeries.unit)
        assertEquals(3, textSeries.pointCount)
        assertEquals(24.0, textSeries.firstValue)
        assertEquals(24.0, textSeries.lastValue)
        assertEquals(23.0, textSeries.minimumValue)
        assertEquals(24.0, textSeries.maximumValue)
        assertEquals(DescriptiveTrendDirection.UNCHANGED, textSeries.direction)
        assertEquals(
            listOf(MaintenanceActionType.FILTER_MAINTENANCE),
            result.textAlternative.annotations.single().maintenanceActionTypes,
        )
        val textComparison = result.textAlternative.interventionComparison!!.series.single()
        assertIs<InterventionSeriesComparison.Available>(textComparison)
        assertEquals(23.0, textComparison.beforeAverage)
        assertEquals(24.0, textComparison.afterAverage)
    }

    private fun advancedSnapshot(
        series: List<ParameterTrendSeries>,
        events: List<TrendEventMarker> = emptyList(),
    ) = AdvancedTrendSnapshot(
        aquarium = Aquarium("aquarium", "Test", 100.0, VolumeUnit.LITERS, 0L),
        windowStartEpochMillis = 0L,
        windowEndEpochMillis = 20 * DAY,
        series = series,
        events = events,
    )

    private fun series(
        id: String,
        parameter: BuiltInParameter,
        unit: String,
        value: Double,
        vararg extraPoints: TrendPoint,
    ) = ParameterTrendSeries(
        definition = ParameterDefinition(id, "aquarium", parameter, true, 0, unit, 1, null, null),
        points = listOf(TrendPoint("at-start", 0L, value)) + extraPoints,
    )

    private fun List<ComparableTrendGroup>.flattenedIds() = flatMap { group ->
        group.series.map { it.definition.id }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
