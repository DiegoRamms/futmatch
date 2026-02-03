package com.devapplab.data.database.match

import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.TeamType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MatchPlayersTable : Table("match_players") {
    val matchId = uuid("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val team = enumerationByName("team", 10, TeamType::class)
    val joinedAt = long("joined_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(matchId, userId)
}
