package com.maximerollin.aqualog.shared

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AquariumPhotoRepositoryTest {
    private val directory = Files.createTempDirectory("aqualog-photos")
    private val databasePath = directory.resolve("aqualog.db").toAbsolutePath().toString()
    private val database = createAquaLogDatabase(createJvmDatabaseBuilder(databasePath))
    private val store = DirectoryAquariumPhotoStore(directory.resolve("photos").toFile())
    private val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()
    private val repository = RoomAquariumRepository(
        database, ids::next, { 1_700_000_000_000L }, photoStore = store,
    )
    private val bytes = byteArrayOf(1, 2, 3)

    @Test
    fun `setting a photo stores the file and the id`() = runTest {
        val aquarium = repository.createAquarium("Salon", 120.0, VolumeUnit.LITERS)
        repository.acknowledgeSyncOperations(repository.pendingSyncBatch().operations.map(SyncOperation::id))

        val updated = repository.setAquariumPhoto(aquarium.id, bytes)

        val photoId = assertNotNull(updated.photoId)
        assertTrue(bytes.contentEquals(repository.readAquariumPhoto(photoId)))
        assertEquals(photoId, repository.loadSetup(aquarium.id)?.aquarium?.photoId)
        val pending = repository.pendingSyncBatch().operations.single()
        assertEquals(SyncEntityType.AQUARIUM, pending.entityType)
        assertEquals(SyncOperationKind.UPSERT, pending.kind)
        assertEquals(listOf(aquarium.id), photoSyncRows())
    }

    @Test
    fun `replacing a photo deletes the old file`() = runTest {
        val aquarium = repository.createAquarium("Salon", 120.0, VolumeUnit.LITERS)
        val old = assertNotNull(repository.setAquariumPhoto(aquarium.id, bytes).photoId)

        val replaced = assertNotNull(repository.setAquariumPhoto(aquarium.id, byteArrayOf(4)).photoId)

        assertNull(repository.readAquariumPhoto(old))
        assertNotEquals(old, replaced)
        assertTrue(byteArrayOf(4).contentEquals(repository.readAquariumPhoto(replaced)))
    }

    @Test
    fun `removing a photo`() = runTest {
        val aquarium = repository.createAquarium("Salon", 120.0, VolumeUnit.LITERS)
        val photoId = assertNotNull(repository.setAquariumPhoto(aquarium.id, bytes).photoId)

        val removed = repository.setAquariumPhoto(aquarium.id, null)

        assertNull(removed.photoId)
        assertNull(repository.loadSetup(aquarium.id)?.aquarium?.photoId)
        assertNull(repository.readAquariumPhoto(photoId))
        // The remote object must still be deleted.
        assertEquals(listOf(aquarium.id), photoSyncRows())
    }

    @Test
    fun `updateAquarium keeps the stored photo`() = runTest {
        val stale = repository.createAquarium("Salon", 120.0, VolumeUnit.LITERS)
        val photoId = assertNotNull(repository.setAquariumPhoto(stale.id, bytes).photoId)

        val updated = repository.updateAquarium(stale.copy(name = "Nano"))

        assertEquals(photoId, updated.photoId)
        assertEquals("Nano", updated.name)
        assertEquals(photoId, repository.loadSetup(stale.id)?.aquarium?.photoId)
    }

    @Test
    fun `removing the local copy deletes photo files`() = runTest {
        val aquarium = repository.createAquarium("Salon", 120.0, VolumeUnit.LITERS)
        val photoId = assertNotNull(repository.setAquariumPhoto(aquarium.id, bytes).photoId)

        repository.removeAccountAndLocalCopy()

        assertNull(repository.readAquariumPhoto(photoId))
        assertEquals(emptyList(), photoSyncRows())
    }

    @Test
    fun `retainOnly keeps the listed files`() = runTest {
        store.write("a", bytes)
        store.write("b", bytes)

        store.retainOnly(setOf("a"))

        assertNotNull(store.read("a"))
        assertNull(store.read("b"))
    }

    private fun photoSyncRows(): List<String> {
        database.close()
        return BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.prepare("SELECT aquariumId FROM aquarium_photo_syncs").use { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
    }
}
