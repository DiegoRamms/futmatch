package com.devapplab.data.repository.match

import com.devapplab.data.database.match.MatchWhatsAppGuestsTable
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import java.util.UUID

/** Bulk counts for read models that do not need guest names. Requires a DB transaction. */
internal fun countWhatsAppGuestsByMatchId(matchIds: List<UUID>): Map<UUID, Int> {
    if (matchIds.isEmpty()) return emptyMap()

    val count = MatchWhatsAppGuestsTable.id.count()
    return MatchWhatsAppGuestsTable.select(MatchWhatsAppGuestsTable.matchId, count)
        .where { MatchWhatsAppGuestsTable.matchId inList matchIds }
        .groupBy(MatchWhatsAppGuestsTable.matchId)
        .associate { it[MatchWhatsAppGuestsTable.matchId] to it[count].toInt() }
}
