package com.devapplab.data.database.location

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object LocationsTable : Table("locations") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val address = text("address").nullable()
    val countryCode = varchar("country_code", 3).nullable()
    val cityCode = varchar("city_code", 10).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { (System.currentTimeMillis()) }

    override val primaryKey = PrimaryKey(id)
}
