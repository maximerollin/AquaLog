package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AquariumRepositoryContractTest {
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
