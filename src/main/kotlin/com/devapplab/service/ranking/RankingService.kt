package com.devapplab.service.ranking

import com.devapplab.data.repository.ranking.RankingRepository
import com.devapplab.model.AppResult
import com.devapplab.model.ranking.*
import com.devapplab.service.image.ImageService
import com.devapplab.utils.Constants
import com.devapplab.utils.createError
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class RankingService(private val repository: RankingRepository, private val imageService: ImageService) {
    suspend fun summary(userId: UUID): RankingSummary {
        val period = period(YearMonth.now(Clock.systemUTC()))
        val snapshot = repository.getSnapshot(period.startTime, period.endTimeExclusive, userId, RankingCategory.VICTORIES, 0, 0, true)
        val user = snapshot.players.firstOrNull { it.player.id == userId }?.player
        return RankingSummary(period = period, status = if (user == null) RankingParticipation.NOT_PARTICIPATING else RankingParticipation.RANKED,
            position = user?.position, victories = user?.victories ?: 0, matchesPlayed = user?.matchesPlayed ?: 0,
            nextTarget = snapshot.target?.let { RankingTarget(it.position, it.victories) }, calculatedAt = System.currentTimeMillis())
    }

    suspend fun ranking(userId: UUID, year: Int, month: Int, category: RankingCategory, page: Int, pageSize: Int, locale: Locale): AppResult<RankingResponse> {
        if (year !in 1..9999 || month !in 1..12 || page !in 1..1000000 || pageSize !in 1..100) {
            return locale.createError(status = HttpStatusCode.BadRequest)
        }
        val period = period(YearMonth.of(year, month))
        val offset = (page.toLong() - 1) * pageSize
        val snapshot = repository.getSnapshot(period.startTime, period.endTimeExclusive, userId, category, offset, pageSize, false)
        val entries = snapshot.players.map { it.copy(player = resolveAvatar(it.player)) }
        return AppResult.Success(RankingResponse(period = period, category = category,
            podium = entries.filter { it.rowNumber <= 3 }.map { it.player },
            players = entries.filter { it.rowNumber > offset && it.rowNumber <= offset + pageSize }.map { it.player },
            currentUser = entries.firstOrNull { it.player.id == userId }?.player, totalPlayers = snapshot.totalPlayers,
            page = page, pageSize = pageSize, calculatedAt = System.currentTimeMillis()))
    }

    private fun resolveAvatar(player: RankingPlayer): RankingPlayer {
        val avatar = player.avatarUrl?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else imageService.getImageUrl("${Constants.BASE_USER_STORAGE_PATH}/${player.id}/$it")
        }
        return player.copy(avatarUrl = avatar)
    }

    private fun period(month: YearMonth) = RankingPeriod(month.year, month.monthValue,
        month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
}
