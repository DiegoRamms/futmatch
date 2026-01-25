package com.devapplab.model.field

import java.util.*

data class FieldImageBaseInfo(
    val id: UUID,
    val fieldId: UUID,
    val imagePath: String,
    val position: Int
)