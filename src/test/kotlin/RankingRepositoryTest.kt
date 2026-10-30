import com.devapplab.data.repository.ranking.RankingRepositoryImpl
import com.devapplab.model.ranking.RankingCategory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.*

class RankingRepositoryTest {
    @Test fun `eligible attendance scores goals and corrections are read from current records`() = runBlocking {
        val db = Database.connect("jdbc:h2:mem:ranking_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val user = UUID.randomUUID(); val match = UUID.randomUUID(); val absent = UUID.randomUUID()
        transaction(db) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY, \"name\" VARCHAR(100), last_name VARCHAR(100), profile_pic VARCHAR(100), gender VARCHAR(10), country VARCHAR(10))")
            exec("CREATE TABLE matches (id UUID PRIMARY KEY, date_time BIGINT, status VARCHAR(20))")
            exec("CREATE TABLE match_players (match_id UUID, user_id UUID, team VARCHAR(10), status VARCHAR(20), attendance_status VARCHAR(20))")
            exec("CREATE TABLE match_results (match_id UUID PRIMARY KEY, team_a_score INT, team_b_score INT)")
            exec("CREATE TABLE match_player_goals (match_id UUID, user_id UUID, goals_count INT)")
            exec("INSERT INTO users VALUES ('$user','Ana','Ruiz',NULL,'FEMALE','MX'), ('$absent','Luis','Perez',NULL,'MALE','MX')")
            exec("INSERT INTO matches VALUES ('$match', 1000, 'COMPLETED')")
            exec("INSERT INTO match_players VALUES ('$match','$user','A','JOINED','PRESENT'), ('$match','$absent','B','JOINED','NO_SHOW')")
            exec("INSERT INTO match_results VALUES ('$match',2,1)")
            exec("INSERT INTO match_player_goals VALUES ('$match','$user',2), ('$match','$absent',5)")
        }
        try {
            val repo = RankingRepositoryImpl()
            val first = repo.getSnapshot(1000, 2000, user, RankingCategory.VICTORIES, 0, 20, false).players.single().player
            assertEquals("Ana R.", first.name); assertEquals("MX", first.country); assertNull(first.avatarUrl)
            assertEquals(1, first.victories); assertEquals(2, first.goals)
            assertTrue(repo.getSnapshot(0, 1000, user, RankingCategory.VICTORIES, 0, 20, false).players.isEmpty())
            transaction(db) { exec("UPDATE match_results SET team_b_score=2 WHERE match_id='$match'"); exec("UPDATE match_player_goals SET goals_count=1 WHERE match_id='$match' AND user_id='$user'") }
            val corrected = repo.getSnapshot(1000, 2000, user, RankingCategory.VICTORIES, 0, 20, false).players.single().player
            assertEquals(0, corrected.victories); assertEquals(1, corrected.goals)
            transaction(db) { exec("UPDATE matches SET status='CANCELED' WHERE id='$match'") }
            assertTrue(repo.getSnapshot(1000, 2000, user, RankingCategory.VICTORIES, 0, 20, false).players.isEmpty())

            val ids = (1..4).map { UUID.fromString("00000000-0000-0000-0000-00000000000$it") }
            val secondMatch = UUID.randomUUID()
            transaction(db) {
                ids.forEach { id -> exec("INSERT INTO users VALUES ('$id','Player','Test',NULL,'MALE','MX')") }
                exec("INSERT INTO matches VALUES ('$secondMatch', 1500, 'COMPLETED')")
                exec("INSERT INTO match_results VALUES ('$secondMatch', 2, 0)")
                ids.forEachIndexed { index, id ->
                    val team = if (index < 2) "A" else "B"
                    exec("INSERT INTO match_players VALUES ('$secondMatch','$id','$team','JOINED','PRESENT')")
                }
                exec("INSERT INTO match_player_goals VALUES ('$secondMatch','${ids[2]}',3)")
            }
            val page = repo.getSnapshot(1000, 2000, ids[3], RankingCategory.VICTORIES, 2, 1, false)
            assertEquals(4, page.totalPlayers)
            assertEquals(listOf(1, 1, 3, 3), page.players.map { it.player.position })
            assertEquals(ids[3], page.players.last().player.id)
            val summary = repo.getSnapshot(1000, 2000, ids[3], RankingCategory.VICTORIES, 0, 0, true)
            assertEquals(1, summary.players.size)
            assertEquals(1, summary.target?.position)
            assertEquals(1, summary.target?.victories)
            assertNull(repo.getSnapshot(1000, 2000, ids[0], RankingCategory.VICTORIES, 0, 0, true).target)
            val goals = repo.getSnapshot(1000, 2000, ids[2], RankingCategory.GOALS, 20, 2, false)
            assertEquals(ids[2], goals.players.first().player.id)
            assertEquals(1, goals.players.first().player.position)
            assertEquals(4, goals.totalPlayers)
        } finally { transaction(db) { exec("DROP ALL OBJECTS") } }
    }
}
