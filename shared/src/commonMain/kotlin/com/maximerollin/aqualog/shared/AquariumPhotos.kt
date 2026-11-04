package com.maximerollin.aqualog.shared

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local files of Aquarium photos, one JPEG per photo id. */
interface AquariumPhotoStore {
    suspend fun write(photoId: String, jpeg: ByteArray)

    suspend fun read(photoId: String): ByteArray?

    suspend fun delete(photoId: String)

    /** Deletes every stored photo whose id is not in [photoIds]. */
    suspend fun retainOnly(photoIds: Set<String>)

    suspend fun clear()
}

private const val RECENT_PHOTO_MILLIS = 5 * 60_000L

private val SAFE_PHOTO_ID = Regex("[A-Za-z0-9-]{1,64}")

/** Photo ids reach the store from the server, so only plain file names are accepted. */
fun isSafePhotoId(photoId: String) = SAFE_PHOTO_ID.matches(photoId)

class DirectoryAquariumPhotoStore(private val directory: File) : AquariumPhotoStore {
    private fun file(photoId: String): File {
        require(isSafePhotoId(photoId)) { "Unsafe photo id" }
        return File(directory, "$photoId.jpg")
    }

    override suspend fun write(photoId: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(photoId)
        directory.mkdirs()
        val temporary = File(directory, "$photoId.jpg.tmp")
        try {
            temporary.writeBytes(jpeg)
            if (!temporary.renameTo(target)) throw IOException("Could not store photo $photoId")
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    override suspend fun read(photoId: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!isSafePhotoId(photoId)) return@withContext null
        file(photoId).takeIf(File::isFile)?.readBytes()
    }

    override suspend fun delete(photoId: String) = withContext(Dispatchers.IO) {
        file(photoId).delete()
        Unit
    }

    override suspend fun retainOnly(photoIds: Set<String>) = withContext(Dispatchers.IO) {
        // A recent file may belong to a photo whose row is not committed yet; a later sync removes real orphans.
        val recent = System.currentTimeMillis() - RECENT_PHOTO_MILLIS
        directory.listFiles { photo ->
            photo.lastModified() < recent &&
                (photo.name.endsWith(".jpg.tmp") || photo.extension == "jpg" && photo.nameWithoutExtension !in photoIds)
        }?.forEach(File::delete)
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach(File::delete)
        Unit
    }
}
