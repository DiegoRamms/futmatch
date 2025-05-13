package model.field

import java.util.*


data class FieldBaseInfo(
    val id: UUID,
    val name: String,
    val location: String,
    val price: Double,
    val capacity: Int,
    val description: String,
    val rules: String,
)