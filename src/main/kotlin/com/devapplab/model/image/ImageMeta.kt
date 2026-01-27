package com.devapplab.model.image

data class ImageMeta(
    val mime: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val checksum: String
)