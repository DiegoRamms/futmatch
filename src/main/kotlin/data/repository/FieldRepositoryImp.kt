package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import io.ktor.server.plugins.*
import model.field.Field
import model.field.FieldBaseInfo
import java.util.*

class FieldRepositoryImp(private val fieldDao: FieldDao, private val fieldImageDao: FieldImageDao) : FieldRepository {
    override suspend fun createField(field: Field): FieldBaseInfo {
        return fieldDao.createField(field)
    }

    override suspend fun updateField(field: Field, adminId: UUID): Boolean {
        val existingField = field.id?.let { fieldDao.getFieldById(it) }
            ?: throw NotFoundException("Field not found")

        if (existingField.adminId != adminId) {
            throw NotFoundException("You cannot modify this field")
        }

        val updatedRows = fieldDao.updateField(field.id, field)
        return updatedRows
    }

    override suspend fun deleteField(fieldId: UUID, adminId: UUID): Boolean  {
        val field = fieldDao.getFieldById(fieldId)
        if (field?.adminId != adminId) return false
        return fieldDao.deleteFieldById(fieldId)
    }

    override suspend fun createImageField(fieldImage: FieldImage): UUID {
        return fieldImageDao.addFieldImage(fieldImage)
    }

    override suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean {
        return fieldImageDao.updateFieldImage(fieldImage, imageId)
    }

    override suspend fun getImageByKey(key: String): FieldImage? {
        return fieldDao.getImageByKey(key)
    }

    override suspend fun getImagesCountByField(fieldId: UUID): Int {
        return  fieldImageDao.getImagesCountByField(fieldId)
    }

    override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> = dbQuery {
         fieldDao.getFieldsByAdminId(adminId).map { field ->
             val images  = fieldImageDao.getFieldImages().filter { field.id == it.fieldId }
             FieldWithImagesBaseInfo(
                 field = field,
                 images = images
             )
         }
    }

    override suspend fun getFields(): List<FieldWithImagesBaseInfo> {
        TODO("Not yet implemented")
    }
}