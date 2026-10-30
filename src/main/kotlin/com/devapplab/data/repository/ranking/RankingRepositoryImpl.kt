package com.devapplab.data.repository.ranking

import com.devapplab.config.dbQuery
import com.devapplab.data.database.match.MatchPlayerGoalsTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchResultsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.model.match.AttendanceStatus
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.TeamType
import com.devapplab.model.ranking.RankingCategory
import com.devapplab.model.ranking.RankingPlayer
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import java.util.UUID

class RankingRepositoryImpl : RankingRepository {
    override suspend fun getSnapshot(startTime: Long, endTimeExclusive: Long, userId: UUID, category: RankingCategory,
                                     offset: Long, limit: Int, summaryOnly: Boolean): RankingSnapshot = dbQuery {
        val base = MatchPlayersTable
            .innerJoin(MatchTable, { MatchPlayersTable.matchId }, { MatchTable.id })
            .innerJoin(MatchResultsTable, { MatchPlayersTable.matchId }, { MatchResultsTable.matchId })
            .innerJoin(UserTable, { MatchPlayersTable.userId }, { UserTable.id })
        val joined = if (summaryOnly) base else base.join(MatchPlayerGoalsTable, JoinType.LEFT, additionalConstraint = {
            (MatchPlayerGoalsTable.matchId eq MatchPlayersTable.matchId) and
                (MatchPlayerGoalsTable.userId eq MatchPlayersTable.userId)
        })
        val won = ((MatchPlayersTable.team eq TeamType.A) and (MatchResultsTable.teamAScore greater MatchResultsTable.teamBScore)) or
            ((MatchPlayersTable.team eq TeamType.B) and (MatchResultsTable.teamBScore greater MatchResultsTable.teamAScore))
        val matches = MatchPlayersTable.userId.count().alias("matches_played")
        val victories = Sum(Case().When(won, intLiteral(1)).Else(intLiteral(0)), IntegerColumnType()).alias("victories")
        val goals = if (summaryOnly) intLiteral(0).alias("goals") else
            Sum(Coalesce(MatchPlayerGoalsTable.goalsCount, intLiteral(0)), IntegerColumnType()).alias("goals")
        val fields = listOf(UserTable.id, UserTable.name, UserTable.lastName, UserTable.profilePic, UserTable.gender, UserTable.country)
        val totals = joined.select(fields + listOf(matches, victories, goals)).where {
            (MatchTable.status eq MatchStatus.COMPLETED) and
                (MatchTable.dateTime greaterEq startTime) and (MatchTable.dateTime less endTimeExclusive) and
                (MatchPlayersTable.attendanceStatus eq AttendanceStatus.PRESENT) and
                (MatchPlayersTable.status inList listOf(MatchPlayerStatus.JOINED, MatchPlayerStatus.RESERVED))
        }.groupBy(*fields.toTypedArray()).alias("totals")
        val score = if (category == RankingCategory.VICTORIES) totals[victories] else totals[goals]
        val position = rank().over().orderBy(score, SortOrder.DESC).alias("position")
        val rowNumber = rowNumber().over().orderBy(score to SortOrder.DESC, totals[UserTable.id] to SortOrder.ASC).alias("row_number")
        val total = totals[UserTable.id].count().over().alias("total_players")
        val rankedMatches = totals[matches].alias("matches_played")
        val rankedVictories = totals[victories].alias("victories")
        val rankedGoals = totals[goals].alias("goals")
        val rankedFields = fields.map { totals[it as Column<*>] }
        val ranked = totals.select(rankedFields + listOf(rankedMatches, rankedVictories, rankedGoals, position, rowNumber, total)).alias("ranked")
        val previousGroup = WindowFrameBound.offsetPreceding(1)
        val nextVictories = ranked[rankedVictories].max().over().orderBy(ranked[rankedVictories], SortOrder.DESC)
            .groups(previousGroup, previousGroup).alias("next_victories")
        val nextPosition = ranked[position].min().over().orderBy(ranked[rankedVictories], SortOrder.DESC)
            .groups(previousGroup, previousGroup).alias("next_position")
        val resultMatches = ranked[rankedMatches].alias("matches_played")
        val resultVictories = ranked[rankedVictories].alias("victories")
        val resultGoals = ranked[rankedGoals].alias("goals")
        val resultPosition = ranked[position].alias("position")
        val resultRowNumber = ranked[rowNumber].alias("row_number")
        val resultTotal = ranked[total].alias("total_players")
        val resultFields = rankedFields.map { ranked[it] }
        val withTarget = ranked.select(resultFields + listOf(resultMatches, resultVictories, resultGoals, resultPosition, resultRowNumber, resultTotal, nextVictories, nextPosition)).alias("with_target")
        val idColumn = withTarget[ranked[totals[UserTable.id]]]
        val rowColumn = withTarget[resultRowNumber]
        val selection = if (summaryOnly) {
            idColumn eq userId
        } else {
            (idColumn eq userId) or (rowColumn lessEq 3L) or
                ((rowColumn greater offset) and (rowColumn lessEq offset + limit))
        }
        val entries = mutableListOf<RankingEntry>()
        var target: RankingPlayer? = null
        var totalPlayers = 0
        val selectedFields = resultFields.map { withTarget[it] } + listOf(
            withTarget[resultMatches], withTarget[resultVictories], withTarget[resultGoals],
            withTarget[resultPosition], rowColumn, withTarget[resultTotal], withTarget[nextVictories], withTarget[nextPosition]
        )
        withTarget.select(selectedFields).where { selection }.orderBy(rowColumn to SortOrder.ASC).forEach { row ->
            val lastName = row[withTarget[ranked[totals[UserTable.lastName]]]]
            val player = RankingPlayer(
                id = row[idColumn],
                name = "${row[withTarget[ranked[totals[UserTable.name]]]]} ${lastName.firstOrNull()?.let { "$it." } ?: ""}".trim(),
                avatarUrl = row[withTarget[ranked[totals[UserTable.profilePic]]]], gender = row[withTarget[ranked[totals[UserTable.gender]]]],
                country = row[withTarget[ranked[totals[UserTable.country]]]], position = row[withTarget[resultPosition]].toInt(),
                matchesPlayed = row[withTarget[resultMatches]].toInt(), victories = row[withTarget[resultVictories]] ?: 0,
                goals = row[withTarget[resultGoals]] ?: 0
            )
            totalPlayers = row[withTarget[resultTotal]].toInt()
            if (summaryOnly && player.id == userId) {
                val nextScore = row[withTarget[nextVictories]]
                val nextRank = row[withTarget[nextPosition]]
                if (nextScore != null && nextRank != null) target = player.copy(position = nextRank.toInt(), victories = nextScore)
            }
            entries.add(RankingEntry(player, row[rowColumn]))
        }
        RankingSnapshot(entries, totalPlayers, target)
    }
}
