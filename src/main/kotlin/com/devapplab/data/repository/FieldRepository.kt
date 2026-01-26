package com.devapplab.data.repository

import com.devapplab.model.field.Field
import com.devapplab.model.field.FieldBaseInfo
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import java.util.*

interface FieldRepository {
    fun createField(field: Field): FieldBaseInfo
    fun createImageField(fieldImage: FieldImage): UUID
    fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean
    fun deleteImageField(imageId: UUID): Boolean
    fun getImageByKey(key: String): FieldImage?
    fun getImageById(id: UUID): FieldImage?
    fun getImagesCountByField(fieldId: UUID): Int
    fun existsFieldImageAtPosition(fieldId: UUID, position: Int): Boolean
    fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean
    fun updateField(fieldId: UUID, field: Field): Boolean
    fun deleteField(fieldId: UUID): Boolean
    fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo>
    fun getFields(): List<FieldWithImagesBaseInfo>
    fun getFieldById(fieldId: UUID): Field?
}