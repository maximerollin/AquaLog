package com.maximerollin.aqualog.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AquariumRepositoryContractTest {
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
}
