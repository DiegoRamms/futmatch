package com.devapplab.data.database.match

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.TeamType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchPlayersTable : Table("match_players") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = javaUUID("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val team = enumerationByName("team", 10, TeamType::class)
    val status = enumerationByName("status", 20, MatchPlayerStatus::class).clientDefault { MatchPlayerStatus.RESERVED }
    val joinedAt = long("joined_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(matchId, userId)
}
