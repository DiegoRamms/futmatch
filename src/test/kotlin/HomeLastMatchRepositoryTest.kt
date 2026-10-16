import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.model.match.HomeMatchOutcome
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HomeLastMatchRepositoryTest {
    @Test
    fun `last played match returns visuals and survives missing team snapshots`() = runBlocking {
        val database = Database.connect(
            "jdbc:h2:mem:last_match_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        val userId = UUID.randomUUID()
        val fieldId = UUID.randomUUID()
        val oldMatchId = UUID.randomUUID()
        val lastMatchId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE fields (id UUID PRIMARY KEY, \"name\" VARCHAR(100))")
            exec("CREATE TABLE matches (id UUID PRIMARY KEY, field_id UUID, date_time_end BIGINT, status VARCHAR(20))")
            exec("CREATE TABLE match_players (match_id UUID, user_id UUID, team VARCHAR(10), status VARCHAR(20), attendance_status VARCHAR(20))")
            exec("CREATE TABLE match_results (match_id UUID PRIMARY KEY, team_a_score INT, team_b_score INT)")
            exec("CREATE TABLE match_teams (match_id UUID, team VARCHAR(1), \"name\" VARCHAR(60), color VARCHAR(7), UNIQUE(match_id, team))")
            exec("INSERT INTO fields VALUES ('$fieldId', 'La Jaula')")
            listOf(oldMatchId to 1000L, lastMatchId to 2000L).forEach { (matchId, endTime) ->
                exec("INSERT INTO matches VALUES ('$matchId', '$fieldId', $endTime, 'COMPLETED')")
                exec("INSERT INTO match_players VALUES ('$matchId', '$userId', 'B', 'JOINED', 'PRESENT')")
                exec("INSERT INTO match_results VALUES ('$matchId', 9, 13)")
            }
            exec("INSERT INTO match_teams VALUES ('$oldMatchId', 'A', 'Old A', '#111111'), ('$oldMatchId', 'B', 'Old B', '#222222')")
        }
        try {
            val repository = MatchRepositoryImp()
            val withoutVisuals = assertNotNull(repository.getHomeLastMatch(userId))
            assertEquals(lastMatchId, withoutVisuals.matchId)
            assertEquals(HomeMatchOutcome.WIN, withoutVisuals.outcome)
            assertNull(withoutVisuals.teamVisuals)
            assertNull(repository.getHomeLastMatch(UUID.randomUUID()))

            transaction(database) {
                exec("INSERT INTO match_teams VALUES ('$lastMatchId', 'A', 'Red', '#FF0000')")
            }
            assertNull(assertNotNull(repository.getHomeLastMatch(userId)).teamVisuals)
            transaction(database) {
                exec("INSERT INTO match_teams VALUES ('$lastMatchId', 'B', 'Blue', '#0000FF')")
            }
            val withVisuals = assertNotNull(repository.getHomeLastMatch(userId))
            assertEquals(withoutVisuals, withVisuals.copy(teamVisuals = null))
            val visuals = assertNotNull(withVisuals.teamVisuals)
            assertEquals("Red", visuals.teamA.name)
            assertEquals("#FF0000", visuals.teamA.color)
            assertEquals("Blue", visuals.teamB.name)
            assertEquals("#0000FF", visuals.teamB.color)

        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }
}
