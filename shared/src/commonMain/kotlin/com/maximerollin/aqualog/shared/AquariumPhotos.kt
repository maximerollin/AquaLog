package com.maximerollin.aqualog.shared

import java.io.File
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

class DirectoryAquariumPhotoStore(private val directory: File) : AquariumPhotoStore {
    private fun file(photoId: String) = File(directory, "$photoId.jpg")

    override suspend fun write(photoId: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val temporary = File(directory, "$photoId.jpg.tmp")
        temporary.writeBytes(jpeg)
        if (!temporary.renameTo(file(photoId))) {
            temporary.delete()
            error("Could not store photo $photoId")
        }
    }

    override suspend fun read(photoId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(photoId).takeIf(File::isFile)?.readBytes()
    }

    override suspend fun delete(photoId: String) = withContext(Dispatchers.IO) {
        file(photoId).delete()
        Unit
    }

    override suspend fun retainOnly(photoIds: Set<String>) = withContext(Dispatchers.IO) {
        directory.listFiles { photo -> photo.extension == "jpg" && photo.nameWithoutExtension !in photoIds }
            ?.forEach(File::delete)
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach(File::delete)
        Unit
    }
}
