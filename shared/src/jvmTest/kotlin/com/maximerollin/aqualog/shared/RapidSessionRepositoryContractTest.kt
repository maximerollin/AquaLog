package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RapidSessionRepositoryContractTest {
    @Test
    fun `a rapid Session atomically records Measures maintenance Actions and Events`() = runTest {
        val fixture = sessionFixture()
        val setup = fixture.createConfiguredAquarium()
        val active = setup.parameters.filter(ParameterDefinition::isActive)

        val elapsed = measureTimeMillis {
            fixture.repository.saveRapidSession(
                RapidSessionInput(
                    aquariumId = setup.aquarium.id,
                    occurredAtEpochMillis = 1_700_000_100_000L,
                    idempotencyKey = "tap-1",
                    measurementInputs = mapOf(
                        active[0].id to "24,5",
                        active[1].id to "7.8",
                        active[2].id to "",
                    ),
                    maintenanceActions = listOf(
                        MaintenanceActionInput(
                            type = MaintenanceActionType.WATER_CHANGE,
                            quantity = "25",
                            unit = "%",
                        ),
                        MaintenanceActionInput(
                            type = MaintenanceActionType.FERTILIZATION,
                            quantity = "2,5",
                            unit = "mL",
                            product = "Green",
                        ),
                    ),
                    observation = "Poissons actifs",
                    incident = "Filtre brièvement arrêté",
                ),
            )
        }

        val recorded = assertNotNull(fixture.repository.observeLatestSession(setup.aquarium.id).first())
        assertEquals(2, recorded.measurements.size)
        assertEquals(24.5, recorded.measurements[0].value)
        assertEquals(7.8, recorded.measurements[1].value)
        assertEquals(2, recorded.maintenanceActions.size)
        assertEquals(2.5, recorded.maintenanceActions[1].quantity)
        assertEquals(listOf(EventType.OBSERVATION, EventType.INCIDENT), recorded.events.map(SessionEvent::type))
        assertTrue(elapsed < 300, "Local Session transaction took ${elapsed}ms")

        val nextContext = fixture.repository.loadRapidSessionContext(setup.aquarium.id)
        assertEquals(active.map(ParameterDefinition::id), nextContext.activeParameters.map(ParameterDefinition::id))
        assertEquals(24.5, nextContext.lastMeasurements[active[0].id])
        assertEquals("", nextContext.measurementInputs[active[0].id])
        assertEquals(25.0, nextContext.lastMaintenanceActions[MaintenanceActionType.WATER_CHANGE]?.quantity)
        assertEquals("Green", nextContext.lastMaintenanceActions[MaintenanceActionType.FERTILIZATION]?.product)
        fixture.close()
    }

    @Test
    fun `replaying one save interaction creates only one Session`() = runTest {
        val fixture = sessionFixture()
        val setup = fixture.createConfiguredAquarium()
        val temperature = setup.parameters.first(ParameterDefinition::isActive)
        val input = RapidSessionInput(
            aquariumId = setup.aquarium.id,
            occurredAtEpochMillis = 1_700_000_100_000L,
            idempotencyKey = "same-tap",
            measurementInputs = mapOf(temperature.id to "23.4"),
        )

        val first = fixture.repository.saveRapidSession(input)
        val replay = fixture.repository.saveRapidSession(input)

        assertEquals(first.session.id, replay.session.id)
        assertEquals(1, fixture.repository.sessionCount(setup.aquarium.id))
        assertEquals(1, replay.measurements.size)
        fixture.close()
    }

    @Test
    fun `a recorded Session can be reopened and atomically corrected`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-session-correction")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val firstDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val firstRepository = RoomAquariumRepository(firstDatabase, ids::next, { 1_700_000_000_000L })
        val setup = firstRepository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )
        val active = setup.parameters.filter(ParameterDefinition::isActive)
        val recorded = firstRepository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "tap-correct",
                measurementInputs = mapOf(active[0].id to "24.5", active[1].id to "7.2"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.WATER_CHANGE, "20", "%"),
                ),
                observation = "Avant correction",
            ),
        )
        firstDatabase.close()

        val reopenedDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val reopenedRepository = RoomAquariumRepository(reopenedDatabase, ids::next, { 1_700_000_200_000L })
        val reopened = assertNotNull(reopenedRepository.observeSession(recorded.session.id).first())
        assertEquals(1_700_000_100_000L, reopened.session.occurredAtEpochMillis)
        assertEquals(listOf(24.5, 7.2), reopened.measurements.map(Measurement::value))

        reopenedRepository.updateSession(
            SessionEditInput(
                sessionId = recorded.session.id,
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_300_000L,
                measurementInputs = mapOf(active[0].id to "25,1"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(
                        MaintenanceActionType.FERTILIZATION,
                        quantity = "2,5",
                        unit = "mL",
                        product = "Green",
                    ),
                ),
                incident = "Filtre corrigé",
            ),
        )

        val corrected = assertNotNull(reopenedRepository.observeSession(recorded.session.id).first())
        assertEquals(1_700_000_300_000L, corrected.session.occurredAtEpochMillis)
        assertEquals(listOf(25.1), corrected.measurements.map(Measurement::value))
        assertEquals(listOf(MaintenanceActionType.FERTILIZATION), corrected.maintenanceActions.map(MaintenanceAction::type))
        assertEquals("Green", corrected.maintenanceActions.single().product)
        assertEquals(listOf(EventType.INCIDENT), corrected.events.map(SessionEvent::type))
        assertEquals("Filtre corrigé", corrected.events.single().note)
        reopenedDatabase.close()
    }

    @Test
    fun `deleting a Session removes it and all dependent data`() = runTest {
        val fixture = sessionFixture()
        val setup = fixture.createConfiguredAquarium()
        val parameter = setup.parameters.first(ParameterDefinition::isActive)
        val recorded = fixture.repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = setup.aquarium.id,
                occurredAtEpochMillis = 1_700_000_100_000L,
                idempotencyKey = "tap-delete",
                measurementInputs = mapOf(parameter.id to "24"),
                maintenanceActions = listOf(
                    MaintenanceActionInput(MaintenanceActionType.FILTER_MAINTENANCE),
                ),
                observation = "À supprimer",
            ),
        )

        fixture.repository.deleteSession(recorded.session.id)

        assertNull(fixture.repository.observeSession(recorded.session.id).first())
        assertNull(fixture.repository.observeLatestSession(setup.aquarium.id).first())
        assertEquals(0, fixture.repository.sessionCount(setup.aquarium.id))
        assertTrue(fixture.repository.loadRapidSessionContext(setup.aquarium.id).lastMeasurements.isEmpty())
        assertTrue(fixture.repository.loadRapidSessionContext(setup.aquarium.id).lastMaintenanceActions.isEmpty())
        fixture.close()
    }

    @Test
    fun `configured Aquarium survives the rapid Session schema migration`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-session-migration")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL(
                """
                CREATE TABLE aquariums (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    volume REAL NOT NULL,
                    volumeUnit TEXT NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    profile TEXT NOT NULL DEFAULT 'established'
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE TABLE parameter_definitions (
                    id TEXT NOT NULL PRIMARY KEY,
                    aquariumId TEXT NOT NULL,
                    parameter TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    unit TEXT NOT NULL,
                    precision INTEGER NOT NULL,
                    indicativeMinimum REAL,
                    indicativeMaximum REAL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO aquariums VALUES ('aquarium-1', 'Historique', 80.0, 'liters', 1700000000000, 'established')",
            )
            connection.execSQL(
                "INSERT INTO parameter_definitions VALUES ('parameter-1', 'aquarium-1', 'temperature', 1, 0, '°C', 1, 22.0, 28.0)",
            )
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'f3376c1d0b30166672e56c035007f253')",
            )
            connection.execSQL("PRAGMA user_version = 2")
        }

        val database = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val repository = RoomAquariumRepository(database, { "unused" }, { 1_700_000_100_000L })

        assertEquals("Historique", repository.observeCurrentSetup().first()?.aquarium?.name)
        assertEquals(0, repository.sessionCount("aquarium-1"))
        database.close()
    }

    private fun sessionFixture(): SessionFixture {
        val path = Files.createTempDirectory("aqualog-rapid-session")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(path))
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        return SessionFixture(
            database,
            RoomAquariumRepository(database, ids::next, { 1_700_000_000_000L }),
        )
    }

    private class SessionFixture(
        private val database: AquaLogDatabase,
        val repository: RoomAquariumRepository,
    ) {
        suspend fun createConfiguredAquarium(): AquariumSetup = repository.createConfiguredAquarium(
            name = "Amazonien",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED),
        )

        fun close() = database.close()
    }
}
