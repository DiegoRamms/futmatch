package com.devapplab.data.repository.ranking

import com.devapplab.model.ranking.RankingCategory
import com.devapplab.model.ranking.RankingPlayer
import java.util.UUID

data class RankingEntry(val player: RankingPlayer, val rowNumber: Long)

data class RankingSnapshot(val players: List<RankingEntry>, val totalPlayers: Int, val target: RankingPlayer?)

interface RankingRepository {
    suspend fun getSnapshot(startTime: Long, endTimeExclusive: Long, userId: UUID, category: RankingCategory,
                            offset: Long, limit: Int, summaryOnly: Boolean): RankingSnapshot
}
