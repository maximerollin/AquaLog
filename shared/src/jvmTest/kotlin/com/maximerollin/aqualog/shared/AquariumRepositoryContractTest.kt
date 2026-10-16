package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AquariumRepositoryContractTest {
    @Test
    fun `Room thirteen migrates existing Aquarium into active multi Aquarium state`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-room-13-14")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val current = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        RoomAquariumRepository(current, { "aquarium-1" }, { 1L }).createAquarium(
            "Existing",
            80.0,
            VolumeUnit.LITERS,
        )
        current.close()

        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL("DROP TABLE selected_aquarium")
            connection.execSQL("ALTER TABLE aquariums DROP COLUMN isArchived")
            connection.execSQL(
                "UPDATE room_master_table SET identity_hash = '954d0dd95a58b7427e20e1a1f515177e' WHERE id = 42",
            )
            connection.execSQL("PRAGMA user_version = 13")
        }

        val migrated = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val repository = RoomAquariumRepository(
            migrated,
            { error("Migration must not recreate data") },
            { error("Migration must not recreate data") },
        )
        val aquarium = requireNotNull(repository.observeCurrentAquarium().first())
        assertEquals("Existing", aquarium.name)
        assertEquals(false, aquarium.isArchived)
        migrated.close()
    }

    @Test
    fun `Pro can select and archive multiple Aquariums while Free downgrade preserves them`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-multi-aquarium-test")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
        val database = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val repository = RoomAquariumRepository(
            database = database,
            generateId = ids::next,
            currentTimeMillis = { 1_700_000_000_000L },
        )
        val parameters = OnboardingPresets.parametersFor(AquariumProfile.ESTABLISHED)

        val first = repository.createConfiguredAquarium(
            name = "Salon",
            volume = 120.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.ESTABLISHED,
            parameters = parameters,
            access = AquariumCreationAccess.FREE,
        )
        val second = repository.createConfiguredAquarium(
            name = "Crevettes",
            volume = 30.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.PLANTED_SHRIMP,
            parameters = parameters,
            access = AquariumCreationAccess.PRO,
        )

        repository.selectAquarium(second.aquarium.id)
        assertEquals(second.aquarium.id, repository.observeCurrentAquarium().first()?.id)
        assertEquals(2, repository.observeAquariums(includeArchived = true).first().size)

        repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = first.aquarium.id,
                occurredAtEpochMillis = 1_700_000_000_001L,
                idempotencyKey = "first-session",
                observation = "First Aquarium",
            ),
        )
        repository.saveRapidSession(
            RapidSessionInput(
                aquariumId = second.aquarium.id,
                occurredAtEpochMillis = 1_700_000_000_002L,
                idempotencyKey = "second-session",
                observation = "Second Aquarium",
            ),
        )
        repository.createTask(
            TaskInput(
                aquariumId = second.aquarium.id,
                title = "Feed shrimp",
                recurrence = TaskRecurrence.ONCE,
                firstDueDate = TaskDate(2026, 10, 15),
                minuteOfDay = null,
                timeZoneId = "Europe/Paris",
            ),
        )
        assertEquals(
            listOf(first.aquarium.id),
            repository.observeTimeline(TimelineFilter(aquariumId = first.aquarium.id)).first()
                .map { it.session.aquariumId },
        )
        assertEquals(
            listOf(second.aquarium.id),
            repository.observeTimeline(TimelineFilter(aquariumId = second.aquarium.id)).first()
                .map { it.session.aquariumId },
        )
        assertEquals(
            second.aquarium.id,
            repository.observePendingTaskOccurrences().first().single().aquariumId,
        )

        assertFailsWith<FreeAquariumLimitReached> {
            repository.createConfiguredAquarium(
                name = "Downgrade must not create a third",
                volume = 20.0,
                volumeUnit = VolumeUnit.LITERS,
                profile = AquariumProfile.ESTABLISHED,
                parameters = parameters,
                access = AquariumCreationAccess.FREE,
            )
        }
        assertEquals(2, repository.observeAquariums(includeArchived = true).first().size)

        repository.archiveAquarium(second.aquarium.id)
        assertEquals(first.aquarium.id, repository.observeCurrentAquarium().first()?.id)
        assertEquals(listOf(first.aquarium.id), repository.observeAquariums().first().map(Aquarium::id))
        assertEquals(2, repository.observeAquariums(includeArchived = true).first().size)
        database.close()

        val reopenedDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val reopened = RoomAquariumRepository(
            database = reopenedDatabase,
            generateId = { error("Persisted Aquariums must not be recreated") },
            currentTimeMillis = { error("Persisted Aquariums must not be recreated") },
        )
        assertEquals(first.aquarium.id, reopened.observeCurrentAquarium().first()?.id)
        assertTrue(reopened.observeAquariums(includeArchived = true).first().single { it.id == second.aquarium.id }.isArchived)
        reopenedDatabase.close()
    }

    @Test
    fun `ticket two Aquarium survives the onboarding schema migration`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-migration-test")
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
                    createdAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "INSERT INTO aquariums VALUES ('aquarium-1', 'Historique', 80.0, 'liters', 1700000000000)",
            )
            connection.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '2c2f92b5fa712cf50ac5a503980193f9')",
            )
            connection.execSQL("PRAGMA user_version = 1")
        }

        val migratedDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val repository = RoomAquariumRepository(
            database = migratedDatabase,
            generateId = { error("Migration must not recreate the Aquarium") },
            currentTimeMillis = { error("Migration must not recreate the Aquarium") },
        )

        val setup = requireNotNull(repository.observeCurrentSetup().first())
        assertEquals("Historique", setup.aquarium.name)
        assertEquals(AquariumProfile.ESTABLISHED, setup.aquarium.profile)
        assertTrue(setup.parameters.isEmpty())
        migratedDatabase.close()
    }

    @Test
    fun `created Aquarium is observable after the database is reopened`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-room-test")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()

        val firstDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val firstRepository = RoomAquariumRepository(
            database = firstDatabase,
            generateId = { "aquarium-1" },
            currentTimeMillis = { 1_700_000_000_000L },
        )

        assertNull(firstRepository.observeCurrentAquarium().first())

        firstRepository.createAquarium(
            name = "Amazonien",
            volume = 120.5,
            volumeUnit = VolumeUnit.LITERS,
        )

        assertEquals(
            Aquarium(
                id = "aquarium-1",
                name = "Amazonien",
                volume = 120.5,
                volumeUnit = VolumeUnit.LITERS,
                createdAtEpochMillis = 1_700_000_000_000L,
            ),
            firstRepository.observeCurrentAquarium().first(),
        )
        firstDatabase.close()

        val reopenedDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val reopenedRepository = RoomAquariumRepository(
            database = reopenedDatabase,
            generateId = { error("A persisted Aquarium must not be recreated") },
            currentTimeMillis = { error("A persisted Aquarium must not be recreated") },
        )

        assertEquals("Amazonien", reopenedRepository.observeCurrentAquarium().first()?.name)
        reopenedDatabase.close()
    }

    @Test
    fun `configured Aquarium and edited Parameters are observable after the database is reopened`() = runTest {
        val databasePath = Files.createTempDirectory("aqualog-onboarding-test")
            .resolve("aqualog.db")
            .toAbsolutePath()
            .toString()
        val configuredParameters = OnboardingPresets.parametersFor(AquariumProfile.PLANTED_SHRIMP)
            .toMutableList()
            .apply {
                val tds = removeAt(indexOfFirst { it.parameter == BuiltInParameter.TDS })
                add(
                    0,
                    tds.copy(
                        unit = "µS/cm",
                        precision = 1,
                        indicativeMinimum = 120.0,
                        indicativeMaximum = 240.0,
                    ),
                )
            }
            .mapIndexed { position, parameter -> parameter.copy(position = position) }

        val firstDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val firstRepository = RoomAquariumRepository(
            database = firstDatabase,
            generateId = sequenceOf("aquarium-1", *Array(10) { "parameter-$it" }).iterator()::next,
            currentTimeMillis = { 1_700_000_000_000L },
        )

        firstRepository.createConfiguredAquarium(
            name = "Crevettes",
            volume = 45.0,
            volumeUnit = VolumeUnit.LITERS,
            profile = AquariumProfile.PLANTED_SHRIMP,
            parameters = configuredParameters,
        )

        val createdSetup = requireNotNull(firstRepository.observeCurrentSetup().first())
        assertEquals(AquariumProfile.PLANTED_SHRIMP, createdSetup.aquarium.profile)
        assertEquals(BuiltInParameter.TDS, createdSetup.parameters.first().parameter)
        assertEquals("µS/cm", createdSetup.parameters.first().unit)
        assertEquals(1, createdSetup.parameters.first().precision)
        assertEquals(120.0, createdSetup.parameters.first().indicativeMinimum)
        assertEquals(240.0, createdSetup.parameters.first().indicativeMaximum)
        assertTrue(createdSetup.parameters.first().isActive)
        firstDatabase.close()

        val reopenedDatabase = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
        val reopenedRepository = RoomAquariumRepository(
            database = reopenedDatabase,
            generateId = { error("Persisted onboarding data must not be recreated") },
            currentTimeMillis = { error("Persisted onboarding data must not be recreated") },
        )

        val reopenedSetup = requireNotNull(reopenedRepository.observeCurrentSetup().first())
        assertEquals("Crevettes", reopenedSetup.aquarium.name)
        assertEquals(configuredParameters.size, reopenedSetup.parameters.size)
        assertEquals(BuiltInParameter.TDS, reopenedSetup.parameters.first().parameter)
        reopenedDatabase.close()
    }
}
