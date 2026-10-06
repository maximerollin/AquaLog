package com.maximerollin.aqualog.shared

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineRepositoryContractTest {
    @Test
    fun `Chronology orders grouped Sessions by effective date without duplicating their content`() = runTest {
        timelineFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                setup = setup,
                occurredAt = 1_700_000_100_000L,
                key = "newer",
                measurements = mapOf(temperature.id to "24.5"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
                observation = "Clear water",
            )
            fixture.record(
                setup = setup,
                occurredAt = 1_700_000_000_000L,
                key = "older",
                measurements = mapOf(temperature.id to "23.0"),
            )

            val items = fixture.repository.observeTimeline(TimelineFilter()).first()

            assertEquals(
                listOf(1_700_000_100_000L, 1_700_000_000_000L),
                items.map { it.session.occurredAtEpochMillis },
            )
            val newest = items.first()
            assertEquals(1, newest.measurements.size)
            assertEquals(BuiltInParameter.TEMPERATURE, newest.measurements.single().definition.parameter)
            assertEquals(1, newest.maintenanceActions.size)
            assertEquals(1, newest.events.size)
        }
    }

    @Test
    fun `Chronology combines period Aquarium Parameter and maintenance Action filters`() = runTest {
        timelineFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val otherSetup = fixture.createConfiguredAquarium("Nano")
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            val ph = setup.parameters.first { it.parameter == BuiltInParameter.PH }
            fixture.record(
                setup,
                occurredAt = 1_700_000_100_000L,
                key = "matching",
                measurements = mapOf(temperature.id to "24.5"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
            )
            fixture.record(
                setup,
                occurredAt = 1_700_000_200_000L,
                key = "wrong-parameter",
                measurements = mapOf(ph.id to "7.2"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
            )
            fixture.record(
                setup,
                occurredAt = 1_699_000_000_000L,
                key = "too-old",
                measurements = mapOf(temperature.id to "22.0"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
            )
            fixture.record(
                setup,
                occurredAt = 1_700_000_250_000L,
                key = "wrong-action",
                measurements = mapOf(temperature.id to "25.0"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.PRUNING)),
            )
            val otherTemperature = otherSetup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            fixture.record(
                otherSetup,
                occurredAt = 1_700_000_300_000L,
                key = "wrong-aquarium",
                measurements = mapOf(otherTemperature.id to "24.0"),
                actions = listOf(MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%")),
            )

            val items = fixture.repository.observeTimeline(
                TimelineFilter(
                    sinceEpochMillisInclusive = 1_700_000_000_000L,
                    aquariumId = setup.aquarium.id,
                    parameterDefinitionId = temperature.id,
                    maintenanceActionType = MaintenanceActionType.WATER_CHANGE,
                ),
            ).first()

            assertEquals(listOf(1_700_000_100_000L), items.map { it.session.occurredAtEpochMillis })
        }
    }

    @Test
    fun `Chronology emits a new Session immediately after a local mutation`() = runTest {
        timelineFixture().use { fixture ->
            val setup = fixture.createConfiguredAquarium()
            val temperature = setup.parameters.first { it.parameter == BuiltInParameter.TEMPERATURE }
            assertEquals(emptyList(), fixture.repository.observeTimeline(TimelineFilter()).first())
            val nextEmission = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.repository.observeTimeline(TimelineFilter()).drop(1).first()
            }

            fixture.record(
                setup,
                occurredAt = 1_700_000_100_000L,
                key = "local-write",
                measurements = mapOf(temperature.id to "24.5"),
            )

            val emitted = nextEmission.await()
            assertEquals(1, emitted.size)
            assertEquals(1_700_000_100_000L, emitted.single().session.occurredAtEpochMillis)
        }
    }

    private fun timelineFixture(): TimelineFixture {
        val path = Files.createTempDirectory("aqualog-timeline")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        return TimelineFixture(
            database,
            RoomAquariumRepository(database, ids::next, { 1_700_000_300_000L }),
        )
    }

    private class TimelineFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    ) : AutoCloseable {
        suspend fun createConfiguredAquarium(name: String = "Amazonien"): AquariumSetup =
            repository.createConfiguredAquarium(
                name = name,
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
        ) {
            repository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = occurredAt,
                    idempotencyKey = key,
                    measurementInputs = measurements,
                    maintenanceActions = actions,
                    observation = observation,
                ),
            )
        }

        override fun close() = database.close()
    }
}
