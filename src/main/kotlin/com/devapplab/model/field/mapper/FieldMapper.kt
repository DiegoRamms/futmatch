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
import java.math.BigDecimal
import java.util.*

fun CreateFieldRequest.toField(adminId: UUID): Field {
    return Field(
        name = name,
        price = priceInCents.toBigDecimal().movePointLeft(2),
        adminId = adminId,
        description = description,
        capacity = capacity,
        rules = rules,
        footwearType = footwearType,
        fieldType = fieldType,
        hasParking = hasParking,
        extraInfo = extraInfo
    )
}

fun FieldBaseInfo.toResponse(): FieldResponse {
    return FieldResponse(
        id = this.id,
        name = this.name,
        locationId = this.locationId,
        priceInCents = this.price.multiply(BigDecimal(100)).longValueExact(),
        capacity = this.capacity,
        description = this.description,
        rules = this.rules,
        footwearType = this.footwearType,
        fieldType = this.fieldType,
        hasParking = this.hasParking,
        extraInfo = this.extraInfo,
        location = this.location
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
        price = priceInCents.toBigDecimal().movePointLeft(2),
        description = description,
        capacity = capacity,
        rules = rules,
        footwearType = footwearType,
        fieldType = fieldType,
        hasParking = hasParking,
        extraInfo = extraInfo
    )
}