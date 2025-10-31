package com.devapplab.model.image

import io.ktor.http.*
import java.io.File

data class ImageFileInfo(
    val file: File,
    val contentType: ContentType
)
