package model.field

import java.util.UUID

data class Field(
    val name: String,
    val location: String,
    val price: Double,
    val capacity: Int,
    val adminId: UUID,
    val description: String,
    val rules: String,
    val createdAt: Long ? = null,
    val updatedAt: Long ? = null
)