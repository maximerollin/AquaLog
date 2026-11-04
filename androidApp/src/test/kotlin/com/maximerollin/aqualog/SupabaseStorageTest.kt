package com.maximerollin.aqualog

import com.maximerollin.aqualog.shared.SyncEntityType
import com.maximerollin.aqualog.shared.SyncOperation
import com.maximerollin.aqualog.shared.SyncOperationKind
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
