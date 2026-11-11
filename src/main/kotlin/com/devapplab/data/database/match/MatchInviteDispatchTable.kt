package com.devapplab.data.database.match

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * One row per (match, milestone) once that invite milestone was processed.
 * The primary key keeps each milestone from being sent twice, even across replicas.
 */
object MatchInviteDispatchTable : Table("match_invite_dispatches") {
    val matchId = javaUUID("match_id").references(MatchTable.id, onDelete = ReferenceOption.CASCADE)
    val milestoneHours = integer("milestone_hours")
    val sentAt = long("sent_at")
    val recipients = integer("recipients")

    override val primaryKey = PrimaryKey(matchId, milestoneHours)
}
