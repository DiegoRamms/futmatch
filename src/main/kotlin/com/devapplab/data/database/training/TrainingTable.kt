package com.devapplab.data.database.training

import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.training.TrainingStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object TrainingTable : Table("trainings") {
    val id = javaUUID("id").autoGenerate()
    val fieldId = javaUUID("field_id").references(FieldTable.id)
    val adminId = javaUUID("admin_id").references(UserTable.id)
    val title = varchar("title", 120)
    val coachName = varchar("coach_name", 120)
    val description = text("description")
    val startsAt = long("starts_at")
    val endsAt = long("ends_at")
    val capacity = integer("capacity")
    val priceInCents = long("price_in_cents")
    val status = enumerationByName<TrainingStatus>("status", 16).default(TrainingStatus.DRAFT)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, status, startsAt)
        index(false, adminId, startsAt)
    }
}
