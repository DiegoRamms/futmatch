package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.model.field.*
import com.devapplab.model.location.Location
import com.devapplab.utils.ValueAlreadyExistsException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class FieldRepositoryImp : FieldRepository {

    override suspend fun createField(field: Field): FieldBaseInfo {
        return dbQuery {
            if (FieldTable.selectAll().where { FieldTable.name eq field.name }.any()) {
                throw ValueAlreadyExistsException(field.name)
            }

            val now = System.currentTimeMillis()
            val result = FieldTable.insert {
                it[name] = field.name
                it[locationId] = field.locationId
                it[pricePerPlayer] = field.price.toBigDecimal()
                it[capacity] = field.capacity
                it[adminId] = field.adminId
                it[description] = field.description
                it[rules] = field.rules
                it[createdAt] = now
                it[updatedAt] = now
            }.resultedValues

            val resultRow = result?.firstOrNull()
                ?: throw IllegalStateException("No ResultRow returned by insert. The DB or driver might not support RETURN_GENERATED_KEYS for UUIDs.")

            resultRow.toFieldBaseInfo()
        }
    }

    override suspend fun updateField(fieldId: UUID, field: Field): Boolean {
        return dbQuery {
            FieldTable.update({ FieldTable.id eq fieldId }) {
                it[name] = field.name
                it[locationId] = field.locationId
                it[pricePerPlayer] = field.price.toBigDecimal()
                it[capacity] = field.capacity
                it[adminId] = field.adminId
                it[description] = field.description
                it[rules] = field.rules
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun deleteField(fieldId: UUID): Boolean {
        return dbQuery {
            FieldTable.deleteWhere { id eq fieldId } > 0
        }
    }

    override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> {
        return dbQuery {
            val coAdminFieldIds = FieldAdminsTable
                .selectAll()
                .where { FieldAdminsTable.adminId eq adminId }
                .map { it[FieldAdminsTable.fieldId] }

            val fieldsQuery = FieldTable
                .leftJoin(LocationsTable)
                .selectAll()
                .where { (FieldTable.adminId eq adminId) or (FieldTable.id inList coAdminFieldIds) }

            val fields = fieldsQuery.map { it.toFieldBaseInfo() }
            if (fields.isEmpty()) return@dbQuery emptyList()

            val fieldIds = fields.map { it.id }
            val images = FieldImagesTable
                .selectAll()
                .where { FieldImagesTable.fieldId inList fieldIds }
                .map { it.toFieldImageBaseInfo() }
                .groupBy { it.fieldId }

            fields.map { field ->
                FieldWithImagesBaseInfo(field = field, images = images[field.id] ?: emptyList())
            }
        }
    }

    override suspend fun getFields(): List<FieldWithImagesBaseInfo> {
        return dbQuery {
            val fields = FieldTable
                .leftJoin(LocationsTable)
                .selectAll()
                .map { it.toFieldBaseInfo() }

            if (fields.isEmpty()) return@dbQuery emptyList()

            val fieldIds = fields.map { it.id }
            val images = FieldImagesTable
                .selectAll()
                .where { FieldImagesTable.fieldId inList fieldIds }
                .map { it.toFieldImageBaseInfo() }
                .groupBy { it.fieldId }

            fields.map { field ->
                FieldWithImagesBaseInfo(field = field, images = images[field.id] ?: emptyList())
            }
        }
    }

    override suspend fun getFieldById(fieldId: UUID): Field? {
        return dbQuery {
            FieldTable.selectAll()
                .where { FieldTable.id eq fieldId }
                .singleOrNull()
                ?.toField()
        }
    }

    override suspend fun createImageField(fieldImage: FieldImage): UUID {
        return dbQuery {
            FieldImagesTable.insert {
                it[this.fieldId] = fieldImage.fieldId
                it[key] = fieldImage.key
                it[position] = fieldImage.position
                it[mime] = fieldImage.mime
                it[sizeBytes] = fieldImage.sizeBytes ?: 0
                it[width] = fieldImage.width
                it[height] = fieldImage.height
                it[isPrimary] = fieldImage.isPrimary
                it[createdAt] = fieldImage.createdAt ?: System.currentTimeMillis()
                it[updatedAt] = fieldImage.updatedAt ?: System.currentTimeMillis()
            }[FieldImagesTable.id]
        }
    }

    override suspend fun updateImageField(fieldImage: FieldImage, imageId: UUID): Boolean {
        return dbQuery {
            FieldImagesTable.update({ FieldImagesTable.id eq imageId }) {
                it[key] = fieldImage.key
                it[mime] = fieldImage.mime
                it[sizeBytes] = fieldImage.sizeBytes ?: 0
                it[width] = fieldImage.width
                it[height] = fieldImage.height
                it[updatedAt] = fieldImage.updatedAt ?: System.currentTimeMillis()
            } > 0
        }
    }

    override suspend fun deleteImageField(imageId: UUID): Boolean {
        return dbQuery {
            FieldImagesTable.deleteWhere { id eq imageId } > 0
        }
    }

    override suspend fun getImageByKey(key: String): FieldImage? {
        return dbQuery {
            FieldImagesTable.selectAll()
                .where { FieldImagesTable.key eq key }
                .firstOrNull()
                ?.toFieldImage()
        }
    }

    override suspend fun getImageById(id: UUID): FieldImage? {
        return dbQuery {
            FieldImagesTable.selectAll()
                .where { FieldImagesTable.id eq id }
                .singleOrNull()
                ?.toFieldImage()
        }
    }

    override suspend fun getImagesCountByField(fieldId: UUID): Int {
        return dbQuery {
            FieldImagesTable.selectAll().where { FieldImagesTable.fieldId eq fieldId }.count().toInt()
        }
    }

    override suspend fun existsFieldImageAtPosition(fieldId: UUID, position: Int): Boolean {
        return dbQuery {
            FieldImagesTable.selectAll()
                .where { (FieldImagesTable.fieldId eq fieldId) and (FieldImagesTable.position eq position) }
                .any()
        }
    }

    override suspend fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean {
        return dbQuery {
            val isMainAdmin = FieldTable.selectAll()
                .where { (FieldTable.id eq fieldId) and (FieldTable.adminId eq adminId) }
                .any()
            if (isMainAdmin) return@dbQuery true

            FieldAdminsTable.selectAll()
                .where { (FieldAdminsTable.fieldId eq fieldId) and (FieldAdminsTable.adminId eq adminId) }
                .any()
        }
    }

    private fun ResultRow.toFieldBaseInfo(): FieldBaseInfo {
        val location = this.getOrNull(LocationsTable.id)?.let {
            Location(
                id = it,
                address = this[LocationsTable.address],
                city = this[LocationsTable.city],
                country = this[LocationsTable.country],
                latitude = this[LocationsTable.latitude],
                longitude = this[LocationsTable.longitude]
            )
        }
        return FieldBaseInfo(
            id = this[FieldTable.id],
            name = this[FieldTable.name],
            locationId = this[FieldTable.locationId],
            price = this[FieldTable.pricePerPlayer].toDouble(),
            capacity = this[FieldTable.capacity],
            description = this[FieldTable.description],
            rules = this[FieldTable.rules],
            location = location
        )
    }

    private fun ResultRow.toField(): Field = Field(
        id = this[FieldTable.id],
        name = this[FieldTable.name],
        locationId = this[FieldTable.locationId],
        price = this[FieldTable.pricePerPlayer].toDouble(),
        capacity = this[FieldTable.capacity],
        description = this[FieldTable.description],
        rules = this[FieldTable.rules],
        adminId = this[FieldTable.adminId]
    )

    private fun ResultRow.toFieldImageBaseInfo(): FieldImageBaseInfo = FieldImageBaseInfo(
        id = this[FieldImagesTable.id],
        fieldId = this[FieldImagesTable.fieldId],
        imagePath = this[FieldImagesTable.key],
        position = this[FieldImagesTable.position]
    )

    private fun ResultRow.toFieldImage(): FieldImage =
        FieldImage(
            imageId = this[FieldImagesTable.id],
            fieldId = this[FieldImagesTable.fieldId],
            key = this[FieldImagesTable.key],
            position = this[FieldImagesTable.position],
            mime = this[FieldImagesTable.mime],
            sizeBytes = this[FieldImagesTable.sizeBytes],
            width = this[FieldImagesTable.width],
            height = this[FieldImagesTable.height],
            isPrimary = this[FieldImagesTable.isPrimary],
            createdAt = this[FieldImagesTable.createdAt],
            updatedAt = this[FieldImagesTable.updatedAt]
        )
}