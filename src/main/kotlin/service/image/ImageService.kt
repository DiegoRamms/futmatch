package service.image

import io.ktor.http.content.*
import model.image.ImageData

interface ImageService {
    suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData>
    suspend fun deleteImages(path: String): Boolean
}
