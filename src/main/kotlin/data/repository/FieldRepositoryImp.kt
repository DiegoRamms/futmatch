package com.devapplab.data.repository

import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import model.field.Field
import model.field.FieldBaseInfo
import java.util.*

class FieldRepositoryImp(private val fieldDao: FieldDao, private val fieldImageDao: FieldImageDao) : FieldRepository {
    override suspend fun createField(field: Field): FieldBaseInfo {
        return fieldDao.createField(field)
    }

    override suspend fun updateField(field: Field): Boolean {
        val fieldId = field.id ?: return false
        fieldDao.getFieldById(fieldId) ?: return false

        val updated = fieldDao.updateField(fieldId, field)
        return updated
    }

    override suspend fun deleteField(fieldId: UUID): Boolean {
        return fieldDao.deleteFieldById(fieldId)
    }

    override suspend fun createImageField(fieldImage: FieldImage): UUID {
        return fieldImageDao.addFieldImage(fieldImage)
    }

    override suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean {
        return fieldImageDao.updateFieldImage(fieldImage, imageId)
    }

    override suspend fun deleteImageField(imageId: UUID): Boolean {
        return fieldImageDao.deleteImageField(imageId)
    }

    override suspend fun getImageByKey(key: String): FieldImage? {
        return fieldImageDao.getImageByKey(key)
    }

    override suspend fun getImageById(id: UUID): FieldImage? {
        return fieldImageDao.getImageById(id)
    }

    override suspend fun getImagesCountByField(fieldId: UUID): Int {
        return fieldImageDao.getImagesCountByField(fieldId)
    }

    override suspend fun existsFieldImageAtPosition(fieldId: UUID, position: Int): Boolean {
        return fieldImageDao.existsFieldImageAtPosition(fieldId, position)
    }

    override suspend fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean {
        return fieldDao.isAdminAssignedToField(adminId, fieldId)
    }

    override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> {
        return fieldDao.getFieldsWithImagesByAdminId(adminId)
    }

    override suspend fun getFields(): List<FieldWithImagesBaseInfo> {
        return fieldDao.getFieldsWithImages()
    }
}