package com.devapplab.data.database.match

import com.devapplab.model.match.TeamType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchWhatsAppGuestsTable : Table("match_whatsapp_guests") {
    val id = javaUUID("id").autoGenerate()
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE).index()
    val team = enumerationByName("team", 10, TeamType::class)
    val name = varchar("name", 80)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}
