package com.devapplab.service.image

import com.devapplab.model.image.ImageData
import io.ktor.http.content.*

interface ImageService {
    suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData>
    suspend fun deleteImages(path: String): Boolean
}
