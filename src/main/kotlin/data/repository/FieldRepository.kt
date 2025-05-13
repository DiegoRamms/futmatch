package com.devapplab.data.repository

import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import model.field.Field
import model.field.FieldBaseInfo
import java.util.UUID

interface FieldRepository {
    suspend fun createField(field: Field): FieldBaseInfo
    suspend fun createImageField(fieldImage: FieldImage): UUID?
    suspend fun updateField(field: Field): UUID
    suspend fun deleteField(fieldId: UUID)
    suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo>
    suspend fun getFields(): List<FieldWithImagesBaseInfo>
}