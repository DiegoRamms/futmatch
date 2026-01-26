package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldDao
import com.devapplab.data.database.field.FieldImageDao
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.model.field.*
import com.devapplab.utils.ValueAlreadyExistsException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.util.*

class FieldRepositoryImp : FieldRepository {
    override suspend fun createField(field: Field): FieldBaseInfo = dbQuery {
        if (FieldDao.find { com.devapplab.data.database.field.FieldTable.name eq field.name }.count() > 0) {
            throw ValueAlreadyExistsException(field.name)
        }

        val now = System.currentTimeMillis()
        val dao = FieldDao.new {
            this.name = field.name
            this.locationId = field.locationId
            this.pricePerPlayer = field.price.toBigDecimal()
            this.capacity = field.capacity
            this.adminId = field.adminId
            this.description = field.description
            this.rules = field.rules
            this.createdAt = now
            this.updatedAt = now
        }
        dao.toFieldBaseInfo()
    }

    override suspend fun updateField(fieldId: UUID, field: Field): Boolean = dbQuery {
        FieldDao.findById(fieldId)?.apply {
            this.name = field.name
            this.locationId = field.locationId
            this.pricePerPlayer = field.price.toBigDecimal()
            this.capacity = field.capacity
            this.adminId = field.adminId
            this.description = field.description
            this.rules = field.rules
            this.updatedAt = System.currentTimeMillis()
        } != null
    }

    override suspend fun deleteField(fieldId: UUID): Boolean = dbQuery {
        FieldDao.findById(fieldId)?.delete()
        true
    }

    override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> = dbQuery {
        FieldDao.find {
            (com.devapplab.data.database.field.FieldTable.adminId eq adminId) or
                    (com.devapplab.data.database.field.FieldAdminsTable.adminId eq adminId)
        }.map { fieldDao ->
            val images = FieldImageDao.find { FieldImagesTable.fieldId eq fieldDao.id.value }
                .map { it.toFieldImageBaseInfo() }
            FieldWithImagesBaseInfo(field = fieldDao.toFieldBaseInfo(), images = images)
        }
    }

    override suspend fun getFields(): List<FieldWithImagesBaseInfo> = dbQuery {
        FieldDao.all().map { fieldDao ->
            val images = FieldImageDao.find { FieldImagesTable.fieldId eq fieldDao.id.value }
                .map { it.toFieldImageBaseInfo() }
            FieldWithImagesBaseInfo(field = fieldDao.toFieldBaseInfo(), images = images)
        }
    }

    override suspend fun getFieldById(fieldId: UUID): Field? = dbQuery {
        FieldDao.findById(fieldId)?.toField()
    }

    override suspend fun createImageField(fieldImage: FieldImage): UUID = dbQuery {
        val dao = FieldImageDao.new {
            this.fieldId = fieldImage.fieldId
            this.key = fieldImage.key
            this.position = fieldImage.position
            this.mime = fieldImage.mime
            this.sizeBytes = fieldImage.sizeBytes ?: 0
            this.width = fieldImage.width
            this.height = fieldImage.height
            this.isPrimary = fieldImage.isPrimary
            this.createdAt = fieldImage.createdAt ?: System.currentTimeMillis()
            this.updatedAt = fieldImage.updatedAt ?: System.currentTimeMillis()
        }
        dao.id.value
    }

    override suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean = dbQuery {
        FieldImageDao.findById(imageId)?.apply {
            this.key = fieldImage.key
            this.mime = fieldImage.mime
            this.sizeBytes = fieldImage.sizeBytes ?: 0
            this.width = fieldImage.width
            this.height = fieldImage.height
            this.updatedAt = fieldImage.updatedAt ?: System.currentTimeMillis()
        } != null
    }

    override suspend fun deleteImageField(imageId: UUID): Boolean = dbQuery {
        FieldImageDao.findById(imageId)?.delete()
        true
    }

    override suspend fun getImageByKey(key: String): FieldImage? = dbQuery {
        FieldImageDao.find { FieldImagesTable.key eq key }.firstOrNull()?.toFieldImage()
    }

    override suspend fun getImageById(id: UUID): FieldImage? = dbQuery {
        FieldImageDao.findById(id)?.toFieldImage()
    }

    override suspend fun getImagesCountByField(fieldId: UUID): Int = dbQuery {
        FieldImageDao.find { FieldImagesTable.fieldId eq fieldId }.count().toInt()
    }

    override suspend fun existsFieldImageAtPosition(fieldId: UUID, position: Int): Boolean = dbQuery {
        FieldImageDao.find { (FieldImagesTable.fieldId eq fieldId) and (FieldImagesTable.position eq position) }.count() > 0
    }

    override suspend fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean = dbQuery {
        FieldDao.findById(fieldId)?.let { field ->
            field.adminId == adminId || field.admins.any { it.id.value == adminId }
        } ?: false
    }

    private fun FieldDao.toFieldBaseInfo(): FieldBaseInfo = FieldBaseInfo(
        id = this.id.value,
        name = this.name,
        locationId = this.locationId,
        price = this.pricePerPlayer.toDouble(),
        capacity = this.capacity,
        description = this.description,
        rules = this.rules,
    )

    private fun FieldDao.toField(): Field = Field(
        id = this.id.value,
        name = this.name,
        locationId = this.locationId,
        price = this.pricePerPlayer.toDouble(),
        capacity = this.capacity,
        description = this.description,
        rules = this.rules,
        adminId = this.adminId
    )

    private fun FieldImageDao.toFieldImageBaseInfo(): FieldImageBaseInfo = FieldImageBaseInfo(
        id = this.id.value,
        fieldId = this.fieldId,
        imagePath = this.key,
        position = this.position
    )

    private fun FieldImageDao.toFieldImage(): FieldImage =
        FieldImage(
            imageId = this.id.value,
            fieldId = this.fieldId,
            key = this.key,
            position = this.position,
            mime = this.mime,
            sizeBytes = this.sizeBytes,
            width = this.width,
            height = this.height,
            isPrimary = this.isPrimary,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
}