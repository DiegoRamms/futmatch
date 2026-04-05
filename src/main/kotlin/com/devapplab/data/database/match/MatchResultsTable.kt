package com.devapplab.data.database.match

import com.devapplab.data.database.user.UserTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object MatchResultsTable : Table("match_results") {
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val teamAScore = integer("team_a_score").default(0)
    val teamBScore = integer("team_b_score").default(0)
    val bestPlayerId = javaUUID("best_player_id").nullable()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val updatedAt = long("updated_at").clientDefault { (System.currentTimeMillis()) }

    override val primaryKey = PrimaryKey(matchId)
}
