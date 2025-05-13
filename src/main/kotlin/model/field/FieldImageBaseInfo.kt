package model.field

import java.util.*

data class FieldImageBaseInfo(
    val id: UUID,
    val fieldId: UUID,
    val imageUrl: String,
    val position: Int
)