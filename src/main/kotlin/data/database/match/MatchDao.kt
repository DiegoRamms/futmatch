package com.devapplab.data.database.match

import com.devapplab.config.dbQuery
import data.database.match.MatchTable
import model.match.Match
import model.match.MatchBaseInfo
import model.match.MatchStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.util.*

class MatchDao {
    suspend fun createMatch(match: Match): MatchBaseInfo = dbQuery {
        println(match)
        val result = MatchTable.insert {
            it[fieldId] = match.fieldId
            it[adminId] = match.adminId
            it[dateTime] = match.dateTime
            it[dateTimeEnd] = match.dateTimeEnd
            it[maxPlayers] = match.maxPlayers
            it[minPlayersRequired] = match.minPlayersRequired
            it[matchPrice] = match.matchPrice
            it[discount] = match.discount
            it[status] = match.status
        }

        val resultRow = result.resultedValues?.firstOrNull()
            ?: throw IllegalStateException("No ResultRow returned by insert. The DB or driver might not support RETURN_GENERATED_KEYS for UUIDs.")

        rowToOpenMatchBaseInfo(resultRow)
    }

    suspend fun cancelMatch(matchId: UUID): Boolean = dbQuery {
        MatchTable.update({ MatchTable.id eq matchId }) {
            it[status] = MatchStatus.CANCELED
        } > 0
    }

    private fun rowToOpenMatchBaseInfo(row: ResultRow): MatchBaseInfo {
        return MatchBaseInfo(
            id = row[MatchTable.id],
            fieldId = row[MatchTable.fieldId],
            dateTime = row[MatchTable.dateTime],
            dateTimeEnd = row[MatchTable.dateTimeEnd],
            maxPlayers = row[MatchTable.maxPlayers],
            minPlayersRequired = row[MatchTable.minPlayersRequired],
            matchPrice = row[MatchTable.matchPrice],
            discountPrice = row[MatchTable.discount],
            status = row[MatchTable.status]
        )
    }

}