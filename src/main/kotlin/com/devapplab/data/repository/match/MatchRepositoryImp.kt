package com.devapplab.data.repository.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.model.match.Match
import com.devapplab.model.match.MatchBaseInfo
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.MatchWithFieldBaseInfo
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.util.*

class MatchRepositoryImp : MatchRepository {
    override suspend fun create(match: Match): MatchBaseInfo {
        val result = dbQuery {
            MatchTable.insert {
                it[fieldId] = match.fieldId
                it[adminId] = match.adminId
                it[dateTime] = match.dateTime
                it[dateTimeEnd] = match.dateTimeEnd
                it[maxPlayers] = match.maxPlayers
                it[minPlayersRequired] = match.minPlayersRequired
                it[matchPrice] = match.matchPrice
                it[status] = match.status
            }
        }

        return MatchBaseInfo(
            id = result[MatchTable.id],
            fieldId = result[MatchTable.fieldId],
            dateTime = result[MatchTable.dateTime],
            dateTimeEnd = result[MatchTable.dateTimeEnd],
            maxPlayers = result[MatchTable.maxPlayers],
            minPlayersRequired = result[MatchTable.minPlayersRequired],
            matchPrice = result[MatchTable.matchPrice],
            status = result[MatchTable.status]
        )
    }

    override suspend fun getMatchesByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> {
        return dbQuery {
            (MatchTable innerJoin FieldTable)
                .leftJoin(LocationsTable)
                .selectAll()
                .where { MatchTable.fieldId eq fieldId }
                .map { row ->
                    MatchWithFieldBaseInfo(
                        matchId = row[MatchTable.id],
                        fieldId = row[FieldTable.id],
                        fieldName = row[FieldTable.name],
                        fieldLocation = row.getOrNull(LocationsTable.address) ?: "",
                        matchDateTime = row[MatchTable.dateTime],
                        matchDateTimeEnd = row[MatchTable.dateTimeEnd],
                        matchPrice = row[MatchTable.matchPrice],
                        discount = BigDecimal.ZERO, // Cannot determine discount from current info
                        maxPlayers = row[MatchTable.maxPlayers],
                        minPlayersRequired = row[MatchTable.minPlayersRequired],
                        status = row[MatchTable.status]
                    )
                }
        }
    }

    override suspend fun cancelMatch(matchId: UUID): Boolean {
        return dbQuery {
            MatchTable.update({ MatchTable.id eq matchId }) {
                it[status] = MatchStatus.CANCELED
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }
}