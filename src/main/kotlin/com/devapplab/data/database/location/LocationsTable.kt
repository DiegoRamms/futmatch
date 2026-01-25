package com.devapplab.data.database.location

import org.jetbrains.exposed.dao.id.UUIDTable

object LocationsTable : UUIDTable("locations") {
    val address = text("address").nullable()
    val city = varchar("city", 100).nullable()
    val country = varchar("country", 100).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
}
