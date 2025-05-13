package model.field.response

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class FieldResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val location: String,
    val price: Double,
    val capacity: Int,
    val description: String,
    val rules: String
)