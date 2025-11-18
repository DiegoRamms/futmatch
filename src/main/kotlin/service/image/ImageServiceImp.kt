package com.devapplab.service.image

import com.devapplab.utils.extractImageMeta
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import model.image.ImageData
import service.image.ImageService
import java.io.File
import java.util.*

class ImageServiceImp : ImageService {
    override suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData> {

        val imagesData = mutableListOf<ImageData>()

        return try {
            val uploadDir = File(path).apply { mkdirs() }

            multiPartData.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "image") {
                    var extension = File(part.originalFileName ?: "").extension.ifEmpty { "jpg" }
                    if (extension == "jpeg") extension = "jpg"
                    val fileName = "${UUID.randomUUID()}"
                    val file = File(uploadDir, "$fileName.$extension" )

                    val byteReadChannel = part.provider()
                    byteReadChannel.copyAndClose(file.writeChannel())

                    val meta = extractImageMeta(file)
                    imagesData.add(ImageData(fileName, meta))

                    part.dispose()
                } else part.dispose()
            }
            imagesData
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun deleteImages(path: String): Boolean {
        val file = File(path)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }
}



