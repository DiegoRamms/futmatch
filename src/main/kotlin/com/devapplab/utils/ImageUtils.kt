package com.devapplab.utils

import com.devapplab.model.image.ImageMeta
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.*
import javax.imageio.ImageIO


fun extractImageMeta(file: File): ImageMeta {
    val mime = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    val size = file.length()


    val img = ImageIO.read(file)
    val width = img?.width ?: 0
    val height = img?.height ?: 0


    val md = MessageDigest.getInstance("SHA-256")
    val checksum = file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    return ImageMeta(mime, width, height, size, checksum)
}


fun getFileFromImageMeta(fieldId: UUID?, imageName: String?, mime: String?): File {
    val baseDir = System.getenv("UPLOAD_DIR") ?: "uploads"

    val ext = when (mime?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }


    return File("$baseDir/fields/$fieldId/images/$imageName.$ext")
}

fun createImagePath(fieldId: UUID): String{
    return "uploads/fields/${fieldId}/images"
}

fun getFieldDirectory(fieldId: UUID): File {
    val baseDir = System.getenv("UPLOAD_DIR") ?: "uploads"
    return File("$baseDir/fields/$fieldId")
}