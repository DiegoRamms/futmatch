package com.devapplab.data.database.field

import com.devapplab.config.dbQuery
import com.devapplab.utils.ValueAlreadyExistsException
import data.database.field.FieldTable
import model.field.Field
import model.field.FieldBaseInfo
import org.jetbrains.exposed.sql.*

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

        rowToField(resultRow)
    }

    suspend fun updateField(fieldId: UUID, field: Field): Unit = dbQuery {
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

    suspend fun getFieldById(fieldId: UUID): FieldBaseInfo? = dbQuery {
        FieldTable.selectAll().where { FieldTable.id eq fieldId }
            .limit(1)
            .mapNotNull(::rowToField)
            .singleOrNull()
    }

    fun getFieldsByAdminId(adminId: UUID): List<FieldBaseInfo> {
        return FieldTable.selectAll().where { FieldTable.adminId eq adminId }
            .mapNotNull(::rowToField)
    }

    suspend fun getAllFields(): List<FieldBaseInfo> = dbQuery {
        FieldTable.selectAll()
            .orderBy(FieldTable.createdAt, SortOrder.DESC)
            .map(::rowToField)
    }

    private fun rowToField(row: ResultRow): FieldBaseInfo = FieldBaseInfo(
        id = row[FieldTable.id],
        name = row[FieldTable.name],
        location = row[FieldTable.location],
        price = row[FieldTable.price].toDouble(),
        capacity = row[FieldTable.capacity],
        description = row[FieldTable.description],
        rules = row[FieldTable.rules],
    )

}