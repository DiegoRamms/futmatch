package com.devapplab.data.repository

import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import model.field.Field
import model.field.FieldBaseInfo
import java.util.UUID

interface FieldRepository {
    suspend fun createField(field: Field): FieldBaseInfo
    suspend fun createImageField(fieldImage: FieldImage): UUID
    suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean
    suspend fun getImageByKey(key: String): FieldImage?
    suspend fun getImagesCountByField(fieldId: UUID): Int
    suspend fun updateField(field: Field, adminId: UUID): Boolean
    suspend fun deleteField(fieldId: UUID, adminId: UUID): Boolean
    suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo>
    suspend fun getFields(): List<FieldWithImagesBaseInfo>
}