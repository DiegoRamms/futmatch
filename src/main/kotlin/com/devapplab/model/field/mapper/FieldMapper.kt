package com.devapplab.model.field.mapper

import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldBaseInfo
import com.devapplab.model.field.FieldImageBaseInfo
import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.model.field.request.CreateFieldRequest
import com.devapplab.model.field.request.UpdateFieldRequest
import com.devapplab.model.field.response.FieldImageResponse
import com.devapplab.model.field.response.FieldResponse
import com.devapplab.model.field.response.FieldWithImagesResponse
import java.util.*

fun CreateFieldRequest.toField(adminId: UUID): Field {
    return Field(
        name = name,
        price = price,
        adminId = adminId,
        description = description,
        capacity = capacity,
        rules = rules
    )
}

fun FieldBaseInfo.toResponse(): FieldResponse {
    return FieldResponse(
        id = this.id,
        name = this.name,
        locationId = this.locationId,
        price = this.price,
        capacity = this.capacity,
        description = this.description,
        rules = this.rules
    )
}

fun FieldImageBaseInfo.toResponse(): FieldImageResponse {
    return FieldImageResponse(
        id = id,
        fieldId = fieldId,
        imagePath = imagePath,
        position = position
    )
}

fun FieldWithImagesBaseInfo.toResponse(): FieldWithImagesResponse {
    return FieldWithImagesResponse(
        field = this.field.toResponse(),
        images = this.images.map { it.toResponse() }
    )
}

fun UpdateFieldRequest.toField(adminId: UUID): Field {
    return Field(
        id =  fieldId,
        name = name,
        adminId = adminId,
        locationId = locationId,
        price = price,
        description = description,
        capacity = capacity,
        rules = rules
    )
}