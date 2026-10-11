package com.devapplab.data.database.match

import com.devapplab.model.match.TeamType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/** Immutable visual identity captured for each side of a match. */
object MatchTeamsTable : Table("match_teams") {
    val id = javaUUID("id").autoGenerate().uniqueIndex()
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val team = enumerationByName("team", 1, TeamType::class)
    val name = varchar("name", 60)
    val color = varchar("color", 7)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(matchId, team)
    }
}
