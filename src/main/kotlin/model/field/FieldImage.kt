package com.devapplab.model.field

import java.util.UUID

data class FieldImage(
    val fieldId: UUID,
    val imagePath: String,
    val position: Int,
    val createdAt: Long? = null
)

