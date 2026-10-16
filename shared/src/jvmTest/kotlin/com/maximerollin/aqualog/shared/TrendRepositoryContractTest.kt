package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrendRepositoryContractTest {
    @Test
    fun `Pro custom period reads multi year history and Free downgrade preserves it`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                setup,
                occurredAt = NOW - 4L * 365L * DAY,
                key = "four-years-old",
                measurements = mapOf(temperature.id to "20.0"),
            )
            fixture.record(
                setup,
                occurredAt = NOW - DAY,
                key = "recent",
                measurements = mapOf(temperature.id to "24.0"),
            )

            val pro = fixture.repository.observeAdvancedTrends(
                aquariumId = setup.aquarium.id,
                access = TrendAccess.PRO,
                customWindow = TrendWindow(NOW - 5L * 365L * DAY, NOW - 3L * 365L * DAY),
            ).first()
            assertEquals(listOf(20.0), pro.series.first { it.definition.id == temperature.id }.points.map { it.value })

            val free = fixture.repository.observeAdvancedTrends(
                aquariumId = setup.aquarium.id,
                access = TrendAccess.FREE,
                customWindow = TrendWindow(0L, NOW),
            ).first()
            assertEquals(listOf(24.0), free.series.first { it.definition.id == temperature.id }.points.map { it.value })
            assertEquals(2, fixture.repository.observeTimeline(TimelineFilter()).first().size)
        }
    }

    @Test
    fun `Pro complete history includes annotations even when measurements are incomplete`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                setup,
                occurredAt = NOW - 8L * 365L * DAY,
                key = "old-action-only",
                measurements = emptyMap(),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.FILTER_MAINTENANCE)),
            )
            fixture.record(
                setup,
                occurredAt = NOW - 7L * 365L * DAY,
                key = "old-measurement",
                measurements = mapOf(temperature.id to "22.0"),
            )

            val pro = fixture.repository.observeAdvancedTrends(
                aquariumId = setup.aquarium.id,
                access = TrendAccess.PRO,
            ).first()

            assertEquals(NOW - 8L * 365L * DAY, pro.windowStartEpochMillis)
            assertEquals(listOf(22.0), pro.series.first { it.definition.id == temperature.id }.points.map { it.value })
            assertTrue(pro.events.single().maintenanceActionTypes.contains(MaintenanceActionType.FILTER_MAINTENANCE))
        }
    }

    @Test
    fun `free Trends keep every active Parameter visible when no Measurement exists`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()

            val snapshot = fixture.repository
                .observeFreeTrends(setup.aquarium.id, TrendPeriod.THIRTY_DAYS)
                .first()

            assertEquals(
                setup.parameters.filter(ParameterDefinition::isActive).map(ParameterDefinition::id),
                snapshot.series.map { it.definition.id },
            )
            assertEquals(emptyList(), snapshot.series.flatMap(ParameterTrendSeries::points))
        }
    }

    @Test
    fun `free Trends expose the exact value and effective date of a single point`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                setup = setup,
                occurredAt = NOW - DAY,
                key = "single-point",
                measurements = mapOf(temperature.id to "24.5"),
            )

            val snapshot = fixture.repository
                .observeFreeTrends(setup.aquarium.id, TrendPeriod.SEVEN_DAYS)
                .first()
            val series = snapshot.series.first { it.definition.id == temperature.id }

            assertEquals("°C", series.definition.unit)
            assertEquals(
                listOf(TrendPoint("id-12", NOW - DAY, 24.5)),
                series.points,
            )
        }
    }

    @Test
    fun `free Trends expose maintenance and Events as contextual markers`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                setup = setup,
                occurredAt = NOW - DAY,
                key = "context",
                measurements = mapOf(temperature.id to "24.5"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                incident = "Heater stopped",
            )

            val snapshot = fixture.repository
                .observeFreeTrends(setup.aquarium.id, TrendPeriod.SEVEN_DAYS)
                .first()

            assertEquals(
                listOf(
                    TrendEventMarker(
                        sessionId = "id-12",
                        occurredAtEpochMillis = NOW - DAY,
                        maintenanceActionTypes = listOf(MaintenanceActionType.WATER_CHANGE),
                        events = listOf(TrendEvent(EventType.INCIDENT, "Heater stopped")),
                    ),
                ),
                snapshot.events,
            )
        }
    }

    @Test
    fun `free Trends cap consultation at 90 days without deleting older history or mixing units`() = runTest {
        trendFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
            fixture.record(
                setup,
                occurredAt = NOW - 90L * DAY,
                key = "boundary",
                measurements = mapOf(temperature.id to "22.5"),
            )
            fixture.record(
                setup,
                occurredAt = NOW - 3L * 365L * DAY,
                key = "three-years-old",
                measurements = mapOf(temperature.id to "21.0"),
            )
            fixture.record(
                setup,
                occurredAt = NOW + DAY,
                key = "future",
                measurements = mapOf(temperature.id to "27.0"),
            )
            fixture.record(
                setup,
                occurredAt = NOW,
                key = "ph-now",
                measurements = mapOf(ph.id to "7.2"),
            )

            val snapshot = fixture.repository
                .observeFreeTrends(setup.aquarium.id, TrendPeriod.NINETY_DAYS)
                .first()
            val temperatureSeries = snapshot.series.first { it.definition.id == temperature.id }
            val phSeries = snapshot.series.first { it.definition.id == ph.id }

            assertEquals("°C", temperatureSeries.definition.unit)
            assertEquals(listOf(22.5), temperatureSeries.points.map(TrendPoint::value))
            assertEquals("pH", phSeries.definition.unit)
            assertEquals(listOf(7.2), phSeries.points.map(TrendPoint::value))
            assertEquals(4, fixture.repository.observeTimeline(TimelineFilter()).first().size)
        }
    }

    private fun trendFixture(): TrendFixture {
        val path = Files.createTempDirectory("aqualog-trends")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        return TrendFixture(
            database,
            RoomAquariumRepository(database, ids::next, { NOW }),
        )
    }

    private class TrendFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    ) : AutoCloseable {
        suspend fun createConfiguredAquarium(): AquariumSetup =
            repository.createConfiguredAquarium(
                name = "Amazonien",
                volume = 120.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
            )

        suspend fun record(
            setup: AquariumSetup,
            occurredAt: Long,
            key: String,
            measurements: Map<String, String>,
            actions: List<MaintenanceActionInput> = emptyList(),
            observation: String = "",
            incident: String = "",
        ): RecordedSession = repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = occurredAt,
                idempotencyKey = key,
                measurementInputs = measurements,
                maintenanceActions = actions,
                observation = observation,
                incident = incident,
            ),
        )

        override fun close() = database.close()
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
        const val NOW = 1_800_000_000_000L
    }
}
