package com.devapplab.data.database.match

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object MatchResultsTable : Table("match_results") {
    val matchId = uuid("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val teamAScore = integer("team_a_score").default(0)
    val teamBScore = integer("team_b_score").default(0)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(matchId)
}
