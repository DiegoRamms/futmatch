package com.devapplab.data.database.notification

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object NotificationTable : Table("notifications") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val title = varchar("title", 255)
    val body = text("body")
    val notificationType = varchar("notification_type", 50)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val metadata = text("metadata").nullable()
    val isRead = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(id)
}
