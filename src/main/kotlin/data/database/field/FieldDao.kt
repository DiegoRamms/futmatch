package com.devapplab.data.database.field

import com.devapplab.config.dbQuery
import com.devapplab.model.field.FieldImage
import com.devapplab.utils.ValueAlreadyExistsException
import data.database.field.FieldImagesTable
import data.database.field.FieldTable
import model.field.Field
import model.field.FieldBaseInfo
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
            it[location] = field.location
            it[price] = field.price.toBigDecimal()
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
            it[location] = field.location
            it[price] = field.price.toBigDecimal()
            it[capacity] = field.capacity
            it[adminId] = field.adminId
            it[description] = field.description
            it[rules] = field.rules
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }



    suspend fun getBaseFieldById(fieldId: UUID): FieldBaseInfo? = dbQuery {
        FieldTable.selectAll().where { FieldTable.id eq fieldId }
            .limit(1)
            .mapNotNull(::rowToBaseField)
            .singleOrNull()
    }

    suspend fun getFieldById(fieldId: UUID): Field? = dbQuery {
        FieldTable.selectAll().where { FieldTable.id eq fieldId }
            .limit(1)
            .mapNotNull(::rowToField)
            .singleOrNull()
    }

    fun getFieldsByAdminId(adminId: UUID): List<FieldBaseInfo> {
        return FieldTable.selectAll().where { FieldTable.adminId eq adminId }
            .mapNotNull(::rowToBaseField)
    }

    suspend fun getAllFields(): List<FieldBaseInfo> = dbQuery {
        FieldTable.selectAll()
            .orderBy(FieldTable.createdAt, SortOrder.DESC)
            .map(::rowToBaseField)
    }

    suspend fun deleteFieldById(id: UUID): Boolean = dbQuery {
        FieldTable.deleteWhere { FieldTable.id eq id } > 0
    }

    private fun rowToBaseField(row: ResultRow): FieldBaseInfo = FieldBaseInfo(
        id = row[FieldTable.id],
        name = row[FieldTable.name],
        location = row[FieldTable.location],
        price = row[FieldTable.price].toDouble(),
        capacity = row[FieldTable.capacity],
        description = row[FieldTable.description],
        rules = row[FieldTable.rules],
    )

    private fun rowToField(row: ResultRow): Field = Field(
        name = row[FieldTable.name],
        location = row[FieldTable.location],
        price = row[FieldTable.price].toDouble(),
        capacity = row[FieldTable.capacity],
        description = row[FieldTable.description],
        rules = row[FieldTable.rules],
        adminId = row[FieldTable.adminId]
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