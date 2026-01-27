package com.devapplab.model.field

import java.util.*

data class FieldImage(
    val imageId: UUID? = null,
    val fieldId: UUID,
    val key: String,
    val position: Int,
    val mime: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isPrimary: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

