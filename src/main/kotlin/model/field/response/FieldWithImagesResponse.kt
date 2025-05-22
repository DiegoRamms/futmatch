package model.field.response

import kotlinx.serialization.Serializable

@Serializable
data class FieldWithImagesResponse(
    val field: FieldResponse,
    val images: List<FieldImageResponse>
)