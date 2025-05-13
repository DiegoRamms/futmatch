package com.devapplab.data.repository

import com.devapplab.config.dbQuery
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

    override suspend fun updateField(field: Field): UUID {
        TODO("Not yet implemented")
    }

    override suspend fun deleteField(fieldId: UUID) {
        TODO("Not yet implemented")
    }

    override suspend fun createImageField(fieldImage: FieldImage): UUID? {
        return fieldImageDao.addFieldImage(fieldImage)
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