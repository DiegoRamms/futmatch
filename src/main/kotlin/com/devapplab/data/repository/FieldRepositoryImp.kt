package com.devapplab.data.repository

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.*
import com.devapplab.model.field.*
import com.devapplab.utils.ValueAlreadyExistsException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class FieldRepositoryImp(private val fieldImageDao: FieldImageDao) : FieldRepository {
    override suspend fun createField(field: Field): FieldBaseInfo = dbQuery {
        val exists = FieldTable
            .select(FieldTable.name).where { FieldTable.name eq field.name }
            .limit(1)
            .count() > 0

        if (exists) {
            throw ValueAlreadyExistsException(field.name)
        }

        val result = FieldTable.insert {
            it[name] = field.name
            it[locationId] = field.locationId
            it[pricePerPlayer] = field.price.toBigDecimal()
            it[capacity] = field.capacity
            it[adminId] = field.adminId
            it[description] = field.description
            it[rules] = field.rules
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }.resultedValues


        val resultRow = result?.firstOrNull()
            ?: throw IllegalStateException("No ResultRow returned by insert. The DB or driver might not support RETURN_GENERATED_KEYS for UUIDs.")

        rowToBaseField(resultRow)
    }

    override suspend fun updateField(fieldId: UUID, field: Field): Boolean = dbQuery {
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

    override suspend fun deleteField(fieldId: UUID): Boolean = dbQuery {
        FieldTable.deleteWhere { FieldTable.id eq fieldId } > 0
    }

    override suspend fun getFieldsByAdminId(adminId: UUID): List<FieldWithImagesBaseInfo> = dbQuery {
        FieldTable
            .leftJoin(FieldAdminsTable, { id }, { fieldId })
            .leftJoin(FieldImagesTable, { FieldTable.id }, { fieldId })
            .selectAll().where {
                (FieldTable.adminId eq adminId) or (FieldAdminsTable.adminId eq adminId)
            }
            .map { row ->
                val field = rowToBaseField(row)

                val image = row.getOrNull(FieldImagesTable.id)?.let {
                    rowToFieldImageBaseInfo(row)
                }

                field to image
            }
            .groupBy { it.first }
            .map { (field, pairs) ->
                val images = pairs.mapNotNull { it.second }
                FieldWithImagesBaseInfo(
                    field = field,
                    images = images
                )
            }
    }

    override suspend fun getFields(): List<FieldWithImagesBaseInfo> = dbQuery {
        FieldTable
            .leftJoin(FieldImagesTable, { id }, { fieldId })
            .selectAll()
            .map { row ->
                val field = rowToBaseField(row)

                val image = row.getOrNull(FieldImagesTable.id)?.let {
                    rowToFieldImageBaseInfo(row)
                }

                field to image
            }
            .groupBy { it.first }
            .map { (field, pairs) ->
                val images = pairs.mapNotNull { it.second }
                FieldWithImagesBaseInfo(
                    field = field,
                    images = images
                )
            }
    }

    override suspend fun getFieldById(fieldId: UUID): Field? = dbQuery {
        FieldTable.selectAll().where { FieldTable.id eq fieldId }
            .limit(1)
            .mapNotNull(::rowToField)
            .singleOrNull()
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

    override suspend fun isAdminAssignedToField(adminId: UUID, fieldId: UUID): Boolean = dbQuery {
        FieldTable
            .leftJoin(FieldAdminsTable, { id }, { FieldAdminsTable.fieldId })
            .selectAll().where {
                (FieldTable.id eq fieldId) and (
                        (FieldTable.adminId eq adminId) or
                                (FieldAdminsTable.adminId eq adminId)
                        )
            }
            .limit(1)
            .any()
    }


    private fun rowToBaseField(row: ResultRow): FieldBaseInfo = FieldBaseInfo(
        id = row[FieldTable.id.],
        name = row[FieldTable.name],
        locationId = row[FieldTable.locationId],
        price = row[FieldTable.pricePerPlayer].toDouble(),
        capacity = row[FieldTable.capacity],
        description = row[FieldTable.description],
        rules = row[FieldTable.rules],
    )

    private fun rowToField(row: ResultRow): Field = Field(
        name = row[FieldTable.name],
        locationId = row[FieldTable.locationId],
        price = row[FieldTable.pricePerPlayer].toDouble(),
        capacity = row[FieldTable.capacity],
        description = row[FieldTable.description],
        rules = row[FieldTable.rules],
        adminId = row[FieldTable.adminId]
    )

    private fun rowToFieldImageBaseInfo(row: ResultRow): FieldImageBaseInfo = FieldImageBaseInfo(
        id = row[FieldImagesTable.id],
        fieldId = row[FieldImagesTable.fieldId],
        imagePath = row[FieldImagesTable.key],
        position = row[FieldImagesTable.position]
    )
}