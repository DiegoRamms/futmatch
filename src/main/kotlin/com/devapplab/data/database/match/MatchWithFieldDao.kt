package com.devapplab.data.database.match

import com.devapplab.config.dbQuery
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.model.match.MatchWithFieldBaseInfo
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.util.*

class MatchWithFieldDao {
    suspend fun getMatchesWithFieldByFieldId(fieldId: UUID): List<MatchWithFieldBaseInfo> = dbQuery {
        (MatchTable
            .innerJoin(FieldTable)
            .leftJoin(LocationsTable)
            .leftJoin(DiscountsTable))
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
            fieldLocation = row.getOrNull(LocationsTable.address).orEmpty(),
            matchDateTime = row[MatchTable.dateTime],
            matchDateTimeEnd = row[MatchTable.dateTimeEnd],
            matchPrice = row[MatchTable.matchPrice],
            discount = row.getOrNull(DiscountsTable.value)?: BigDecimal.ZERO,
            maxPlayers = row[MatchTable.maxPlayers],
            minPlayersRequired = row[MatchTable.minPlayersRequired],
            status = row[MatchTable.status]
        )
    }
}