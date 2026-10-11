package com.devapplab.data.database.team

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * Catalog of selectable team identities. Matches will persist their own snapshot
 * of a selected identity so future catalog edits do not change match history.
 */
object TeamTable : Table("teams") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 60).uniqueIndex()
    val color = varchar("color", 7)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}
