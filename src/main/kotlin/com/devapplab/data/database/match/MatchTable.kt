package com.devapplab.data.database.match

import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.user.PlayerLevel
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchTable : Table("matches") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val fieldId = javaUUID("field_id").references(FieldTable.id, onDelete = ReferenceOption.CASCADE)
    val adminId = javaUUID("admin_id").references(UserTable.id, onDelete = ReferenceOption.NO_ACTION)
    val supervisorId = javaUUID("supervisor_id").nullable()
    val dateTime = long("date_time")
    val dateTimeEnd = long("date_time_end")
    val maxPlayers = integer("max_players")
    val minPlayersRequired = integer("min_players_required")
    val matchPrice = decimal("match_price", 10, 2)
    val status = enumerationByName("status", 20, MatchStatus::class)
    val playerLevel = enumerationByName("player_level", 20, PlayerLevel::class).default(PlayerLevel.ANY)
    val genderType = enumerationByName("gender_type", 20, GenderType::class).default(GenderType.MIXED)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { (System.currentTimeMillis()) }

    override val primaryKey = PrimaryKey(id)
}