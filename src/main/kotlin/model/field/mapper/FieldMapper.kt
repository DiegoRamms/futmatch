package com.devapplab.model.field.mapper

import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.model.field.request.CreateFieldRequest
import model.field.Field
import model.field.FieldBaseInfo
import model.field.FieldImageBaseInfo
import model.field.request.UpdateFieldRequest
import model.field.response.FieldImageResponse
import model.field.response.FieldResponse
import model.field.response.FieldWithImagesResponse
import java.util.*

fun CreateFieldRequest.toField(adminId: UUID): Field {
    return Field(
        name = name,
        location = location,
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
        location = this.location,
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
        location = location,
        price = price,
        description = description,
        capacity = capacity,
        rules = rules
    )
}