package model.field.request

import com.devapplab.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class UpdateFieldRequest(
    @Serializable(with = UUIDSerializer::class)
    val fieldId: UUID,
    val name: String,
    val location: String,
    val price: Double,
    val capacity: Int,
    val description: String,
    val rules: String
)