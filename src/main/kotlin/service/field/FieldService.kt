package com.devapplab.service.field

import com.devapplab.data.repository.FieldRepository
import com.devapplab.model.AppResult
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.model.field.mapper.toResponse
import model.field.Field
import model.field.response.FieldResponse
import java.util.*

class FieldService(
    private val fieldRepository: FieldRepository
) {

    suspend fun createField(field: Field): AppResult<FieldResponse> {
        val fielResponse = fieldRepository.createField(field).toResponse()
        return AppResult.Success(data = fielResponse)
    }

    suspend fun createFieldImage(locale: Locale, fieldImage: FieldImage): AppResult<UUID> {
        val imageId = fieldRepository.createImageField(fieldImage)
            ?: throw Exception("Image field creation failed") //TODO FIX this
        return AppResult.Success(imageId)
    }

    suspend fun updateField(locale: Locale, field: Field): AppResult<UUID> {
        val updatedId = fieldRepository.updateField(field)
        return AppResult.Success(updatedId)
    }

    suspend fun deleteField(locale: Locale, fieldId: UUID): AppResult<String> {
        fieldRepository.deleteField(fieldId)
        return AppResult.Success("Field deleted")
    }

    suspend fun getFieldsByAdminId(locale: Locale, adminId: UUID): AppResult<List<FieldWithImagesBaseInfo>> {
        val fields = fieldRepository.getFieldsByAdminId(adminId)
        return AppResult.Success(fields)
    }

    suspend fun getAllFields(locale: Locale): AppResult<List<FieldWithImagesBaseInfo>> {
        val fields = fieldRepository.getFields()
        return AppResult.Success(fields)
    }



}