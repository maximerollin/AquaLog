package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.Aquarium
import com.maximerollin.aqualog.shared.Inhabitant
import com.maximerollin.aqualog.shared.encyclopedia.DrawingFamily
import com.maximerollin.aqualog.shared.SyncEntityType
import com.maximerollin.aqualog.shared.SyncOperation
import com.maximerollin.aqualog.shared.SyncOperationKind
import com.maximerollin.aqualog.shared.VolumeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

// Bodies below were observed on the local Supabase stack (storage-api answers HTTP 400 to these).
class SupabaseStorageTest {
    private val notFound = """{"statusCode":"404","error":"not_found","message":"Object not found"}"""
    private val expiredToken =
        """{"statusCode":"403","error":"Unauthorized","message":"\"exp\" claim timestamp check failed"}"""
    private val badToken = """{"statusCode":"403","error":"Unauthorized","message":"JWS Protected Header is invalid"}"""
    private val rlsRefusal =
        """{"statusCode":"403","error":"Unauthorized","message":"new row violates row-level security policy"}"""

    @Test
    fun `a missing object is missing whether Storage answers 400 or 404`() {
        assertEquals(StorageFailure.MISSING, classifyStorageFailure(400, notFound))
        assertEquals(StorageFailure.MISSING, classifyStorageFailure(404, ""))
        assertEquals(StorageFailure.MISSING, classifyStorageFailure(400, """{"error":"NoSuchKey"}"""))
    }

    @Test
    fun `an expired or invalid token is an expired session, never a missing object`() {
        assertEquals(StorageFailure.AUTH_EXPIRED, classifyStorageFailure(400, expiredToken))
        assertEquals(StorageFailure.AUTH_EXPIRED, classifyStorageFailure(400, badToken))
        assertEquals(StorageFailure.AUTH_EXPIRED, classifyStorageFailure(400, """{"error":"InvalidJWT"}"""))
        assertEquals(StorageFailure.AUTH_EXPIRED, classifyStorageFailure(401, ""))
    }

    @Test
    fun `other refusals and unreadable bodies are server errors`() {
        assertEquals(StorageFailure.OTHER, classifyStorageFailure(400, rlsRefusal))
        assertEquals(StorageFailure.OTHER, classifyStorageFailure(400, """{"statusCode":"413","error":"Payload too large"}"""))
        assertEquals(StorageFailure.OTHER, classifyStorageFailure(400, "<html>"))
        assertEquals(StorageFailure.OTHER, classifyStorageFailure(400, ""))
        assertEquals(StorageFailure.OTHER, classifyStorageFailure(500, notFound))
    }

    private fun operation(id: Long, type: SyncEntityType, entityId: String, kind: SyncOperationKind = SyncOperationKind.UPSERT) =
        SyncOperation(id, "key-$id", type, entityId, kind, 0, 0L, 0L, 0L)

    @Test
    fun `an Aquarium updated before its first sync is still pushed before the rows that reference it`() {
        val operations = listOf(
            operation(1, SyncEntityType.AQUARIUM, "a"),
            operation(2, SyncEntityType.PARAMETER_DEFINITION, "p1"),
            operation(3, SyncEntityType.PARAMETER_DEFINITION, "p2"),
            operation(4, SyncEntityType.AQUARIUM, "a"),
        )
        assertEquals(listOf(4L, 2L, 3L), operations.inPushOrder().map { it.id })
    }

    @Test
    fun `a Session updated after creation keeps its place and sends its latest operation`() {
        val operations = listOf(
            operation(1, SyncEntityType.SESSION, "s"),
            operation(2, SyncEntityType.MEASUREMENT, "m"),
            operation(3, SyncEntityType.SESSION, "s"),
        )
        assertEquals(listOf(3L, 2L), operations.inPushOrder().map { it.id })
    }

    @Test
    fun `only the upsert that follows a photo upload carries photo_id`() {
        val aquarium = Aquarium("a", "Bac", 60.0, VolumeUnit.LITERS, 0L, photoId = "p1")

        assertFalse(aquarium.toRemoteJson("account", withPhotoId = false).has("photo_id"))
        assertEquals("p1", aquarium.toRemoteJson("account", withPhotoId = true).getString("photo_id"))
        val removed = aquarium.copy(photoId = null).toRemoteJson("account", withPhotoId = true)
        assertTrue(removed.has("photo_id"))
        assertEquals(JSONObject.NULL, removed.get("photo_id"))
    }

    @Test
    fun `an inhabitant added to a new Aquarium is pushed after it`() {
        val operations = listOf(
            operation(1, SyncEntityType.AQUARIUM, "a"),
            operation(2, SyncEntityType.INHABITANT, "i"),
            operation(3, SyncEntityType.AQUARIUM, "a"),
        )
        assertEquals(listOf(3L, 2L), operations.inPushOrder().map { it.id })
    }

    @Test
    fun `an inhabitant round-trips through its row and an unknown family is skipped`() {
        val neons = Inhabitant("i1", "a", "neon", null, DrawingFamily.TETRA, 6, 10L)
        val custom = Inhabitant("i2", "a", null, "Maison", DrawingFamily.CICHLID, 2, 10L, 20L)
        val unknown = JSONObject(neons.toRemoteJson("account").toString()).put("id", "i3").put("family", "KRAKEN")

        val rows = listOf(neons.toRemoteJson("account"), custom.toRemoteJson("account"), unknown).withKnownFamily()

        assertEquals(listOf(neons, custom), rows.map { it.toInhabitant() })
    }
}
