package com.devapplab.data.database.field

import com.devapplab.config.dbQuery
import com.devapplab.model.field.FieldImage
import data.database.field.FieldImagesTable
import model.field.FieldImageBaseInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class FieldImageDao {

    suspend fun addFieldImage(fieldImage: FieldImage): UUID = dbQuery {
        val result = FieldImagesTable.insert {
            it[fieldId] = fieldImage.fieldId
            it[key] = fieldImage.key
            it[position] = fieldImage.position
            it[mime] = fieldImage.mime
            it[sizeBytes] = fieldImage.sizeBytes ?: 0
            it[width] = fieldImage.width
            it[height] = fieldImage.height
            it[isPrimary] = fieldImage.isPrimary
            it[createdAt] = fieldImage.createdAt ?: System.currentTimeMillis()
            it[updatedAt] = fieldImage.updatedAt ?: System.currentTimeMillis()

        }
         result[FieldImagesTable.id]
    }

    suspend fun updateFieldImage(fieldImage: FieldImage, imageId: UUID): Boolean = dbQuery {
        FieldImagesTable.update({ FieldImagesTable.id eq imageId}) {
            it[key] = fieldImage.key
            it[mime] = fieldImage.mime
            it[sizeBytes] = fieldImage.sizeBytes ?: 0
            it[width] = fieldImage.width
            it[height] = fieldImage.height
            it[updatedAt] = fieldImage.updatedAt ?: System.currentTimeMillis()
        } > 0
    }

    suspend fun getImagesCountByField(fieldId: UUID): Int = dbQuery{
        val imageCount = FieldImagesTable
            .selectAll().where { FieldImagesTable.fieldId eq fieldId }
            .count()

        imageCount.toInt() ?: 0
    }

    suspend fun getImagesByField(fieldId: UUID): List<FieldImageBaseInfo> = dbQuery {
        FieldImagesTable
            .selectAll().where { FieldImagesTable.fieldId eq fieldId }
            .orderBy(FieldImagesTable.position to SortOrder.ASC)
            .mapNotNull(::rowToFieldImage)
    }

    suspend fun getFieldImages(): List<FieldImageBaseInfo> = dbQuery {
        FieldImagesTable
            .selectAll()
            .map(::rowToFieldImage)
    }

    suspend fun deleteImagesByField(fieldId: UUID): Unit = dbQuery {
        FieldImagesTable.deleteWhere { FieldImagesTable.fieldId eq fieldId } > 0
    }

    private fun rowToFieldImage(row: ResultRow) = FieldImageBaseInfo(
        id = row[FieldImagesTable.id],
        fieldId = row[FieldImagesTable.fieldId],
        imagePath = row[FieldImagesTable.key],
        position = row[FieldImagesTable.position]
    )

}