package com.devapplab.data.database.field

import com.devapplab.config.dbQuery
import com.devapplab.model.field.FieldImage
import data.database.field.FieldImagesTable
import model.field.FieldImageBaseInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class FieldImageDao {

    suspend fun addFieldImage(fieldImage: FieldImage): UUID? = dbQuery {
        val imageCount = FieldImagesTable
            .selectAll().where { FieldImagesTable.fieldId eq fieldImage.fieldId }
            .count()

        if (imageCount >= 4) return@dbQuery null
        val result = FieldImagesTable.insert {
            it[fieldId] = fieldImage.fieldId
            it[imageUrl] = fieldImage.imagePath
            it[position] = fieldImage.position
        }
         result[FieldImagesTable.id]
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
        imagePath = row[FieldImagesTable.imageUrl],
        position = row[FieldImagesTable.position]
    )

}