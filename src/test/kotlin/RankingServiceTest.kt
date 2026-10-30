import com.devapplab.data.repository.ranking.RankingRepository
import com.devapplab.data.repository.ranking.RankingEntry
import com.devapplab.data.repository.ranking.RankingSnapshot
import com.devapplab.model.AppResult
import com.devapplab.model.ranking.*
import com.devapplab.model.user.Gender
import com.devapplab.service.image.ImageService
import com.devapplab.service.ranking.RankingService
import com.devapplab.model.image.ImageData
import io.ktor.http.content.MultiPartData
import kotlinx.coroutines.runBlocking
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import kotlin.test.*

class RankingServiceTest {
    @Test fun `shared positions pagination targets and user data are consistent`() = runBlocking {
        val ids = (1..4).map { UUID.fromString("00000000-0000-0000-0000-00000000000$it") }
        val rows = listOf(row(ids[0], true, 1), row(ids[0], true, 2), row(ids[1], true, 0), row(ids[1], true, 0),
            row(ids[2], true, 3), row(ids[3], false, 0))
        val repository = FakeRankingRepository(rows)
        val service = RankingService(repository, FakeImages())
        val month = YearMonth.now(ZoneOffset.UTC)
        val response = (service.ranking(ids[2], month.year, month.monthValue, RankingCategory.VICTORIES, 2, 2, Locale.US) as AppResult.Success).data
        assertEquals(listOf(1, 1, 3), response.podium.map { it.position })
        assertEquals(listOf(3, 4), response.players.map { it.position })
        assertEquals(4, response.totalPlayers)
        assertEquals(ids[2], response.currentUser?.id)
        assertEquals("MX", response.currentUser?.country)
        assertEquals(Gender.FEMALE, response.currentUser?.gender)
        assertEquals("image/futmatch/users/${ids[2]}/photo.png", response.currentUser?.avatarUrl)
        val summary = service.summary(ids[2])
        assertEquals(3, summary.position)
        assertEquals(RankingTarget(1, 2), summary.nextTarget)
        assertEquals(response.period, summary.period)
        assertEquals(summary.victories, response.currentUser?.victories)
        assertNull(service.summary(ids[0]).nextTarget)
        assertEquals(RankingParticipation.NOT_PARTICIPATING, service.summary(UUID.randomUUID()).status)
        val goals = (service.ranking(ids[2], month.year, month.monthValue, RankingCategory.GOALS, 1, 20, Locale.US) as AppResult.Success).data
        assertEquals(1, goals.currentUser?.position)
        assertEquals(3, goals.currentUser?.goals)
        assertTrue(service.ranking(ids[0], 2024, 13, RankingCategory.GOALS, 1, 20, Locale.US) is AppResult.Failure)
    }

    private fun row(id: UUID, won: Boolean, goals: Int) = RankingMatchRow(id, "Ana R.", "photo.png", Gender.FEMALE, "MX", won, goals)
    private class FakeRankingRepository(val rows: List<RankingMatchRow>) : RankingRepository {
        override suspend fun getSnapshot(startTime: Long, endTimeExclusive: Long, userId: UUID, category: RankingCategory, offset: Long, limit: Int, summaryOnly: Boolean): RankingSnapshot {
            val totals = rows.groupBy { it.userId }.map { (id, games) ->
                val r = games.first()
                RankingPlayer(id, r.name, r.avatar, r.gender, r.country, 0, games.size, games.count { it.won }, games.sumOf { it.goals })
            }
            fun score(p: RankingPlayer) = if (category == RankingCategory.VICTORIES) p.victories else p.goals
            var previous: Int? = null
            var rank = 0
            val ordered = totals.sortedWith(compareByDescending<RankingPlayer> { score(it) }.thenBy { it.id.toString() }).mapIndexed { i, p ->
                if (score(p) != previous) rank = i + 1
                previous = score(p)
                RankingEntry(p.copy(position = rank), (i + 1).toLong())
            }
            val me = ordered.firstOrNull { it.player.id == userId }?.player
            val target = me?.let { m -> ordered.map { it.player }.filter { it.victories > m.victories }.minByOrNull { it.victories } }
            val selected = ordered.filter { it.player.id == userId || (summaryOnly && it.player.id == target?.id) || (!summaryOnly && (it.rowNumber <= 3 || it.rowNumber > offset && it.rowNumber <= offset + limit)) }
            return RankingSnapshot(selected, ordered.size, if (summaryOnly) target else null)
        }
    }
    private class FakeImages : ImageService {
        override suspend fun saveImages(multiPartData: MultiPartData, path: String): List<ImageData> = error("unused")
        override suspend fun saveGoogleProfileImage(sourceUrl: String, path: String): ImageData? = error("unused")
        override suspend fun deleteImages(path: String): Boolean = error("unused")
        override fun getImageUrl(publicId: String) = "image/$publicId"
    }
}
