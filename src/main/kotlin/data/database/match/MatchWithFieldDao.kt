package com.devapplab.data.database.match

import com.devapplab.config.dbQuery
import data.database.field.FieldTable
import data.database.match.MatchTable
import model.match.MatchBaseInfo
import model.match.MatchWithFieldBaseInfo
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*

class MatchWithFieldDao {
    suspend fun getMatchesWithFieldByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> = dbQuery {
        (MatchTable innerJoin FieldTable)
            .selectAll().where {
                MatchTable.fieldId eq fieldId
            }
            .map(::rowToMatchWithFieldBaseInfo)
    }

    private fun rowToMatchWithFieldBaseInfo(row: ResultRow): MatchWithFieldBaseInfo {
        return MatchWithFieldBaseInfo(
            matchId = row[MatchTable.id],
            fieldId = row[FieldTable.id],
            fieldName = row[FieldTable.name],
            fieldLocation = row[FieldTable.location],
            matchDateTime = row[MatchTable.dateTime],
            matchDateTimeEnd = row[MatchTable.dateTimeEnd],
            matchPrice = row[MatchTable.matchPrice],
            discount = row[MatchTable.discount],
            maxPlayers = row[MatchTable.maxPlayers],
            minPlayersRequired = row[MatchTable.minPlayersRequired],
            status = row[MatchTable.status]
        )
    }
}