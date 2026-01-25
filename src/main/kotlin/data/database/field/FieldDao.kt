package com.devapplab.data.database.field

import com.devapplab.config.dbQuery
import com.devapplab.model.field.FieldImage
import com.devapplab.model.field.FieldWithImagesBaseInfo
import com.devapplab.utils.ValueAlreadyExistsException
import data.database.field.FieldImagesTable
import data.database.field.FieldTable
import model.field.Field
import model.field.FieldBaseInfo
import model.field.FieldImageBaseInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

import java.util.*

class FieldDao {

    suspend fun createField(field: Field): FieldBaseInfo = dbQuery {
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

    suspend fun updateField(fieldId: UUID, field: Field): Boolean = dbQuery {
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

    suspend fun getFieldById(fieldId: UUID): Field? = dbQuery {
        FieldTable.selectAll().where { FieldTable.id eq fieldId }
            .limit(1)
            .mapNotNull(::rowToField)
            .singleOrNull()
    }

    suspend fun getFieldsWithImagesByAdminId(
        adminId: UUID
    ): List<FieldWithImagesBaseInfo> = dbQuery {
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

    suspend fun getFieldsWithImages(): List<FieldWithImagesBaseInfo> = dbQuery {
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

    suspend fun deleteFieldById(id: UUID): Boolean = dbQuery {
        FieldTable.deleteWhere { FieldTable.id eq id } > 0
    }

    suspend fun isAdminAssignedToField(
        adminId: UUID,
        fieldId: UUID
    ): Boolean = dbQuery {
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
        id = row[FieldTable.id],
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

    @Suppress("Unused")
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


    private fun rowToFieldImageBaseInfo(row: ResultRow): FieldImageBaseInfo = FieldImageBaseInfo(
        id = row[FieldImagesTable.id],
        fieldId = row[FieldImagesTable.fieldId],
        imagePath = row[FieldImagesTable.key],
        position = row[FieldImagesTable.position]
    )

}