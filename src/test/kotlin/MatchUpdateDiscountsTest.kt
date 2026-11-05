import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.model.match.GenderType
import com.devapplab.model.match.Match
import com.devapplab.model.user.PlayerLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchUpdateDiscountsTest {
    @Test
    fun `omitted discounts are preserved and explicit lists replace them`() = runBlocking {
        val db = Database.connect("jdbc:h2:mem:update_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val matchId = UUID.randomUUID()
        val fieldId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        transaction(db) {
            exec("""CREATE TABLE matches (id UUID PRIMARY KEY, field_id UUID, supervisor_id UUID,
                date_time BIGINT, date_time_end BIGINT, max_players INT, min_players_required INT,
                match_price DECIMAL(10,2), field_cost_in_cents_snapshot BIGINT, organizer_fee_in_cents_snapshot BIGINT,
                minimum_profit_in_cents_snapshot BIGINT, max_price_per_player_in_cents_snapshot BIGINT,
                futmatch_profit_bps_snapshot INT, payment_provider_snapshot VARCHAR(20), payment_percent_fee_bps_snapshot INT,
                payment_fixed_fee_cents_snapshot BIGINT, price_rounding_step_cents_snapshot BIGINT,
                status VARCHAR(20), gender_type VARCHAR(20), player_level VARCHAR(20), updated_at BIGINT)""")
            exec("CREATE TABLE match_discounts (match_id UUID, discount_id UUID)")
            exec("INSERT INTO matches (id) VALUES ('$matchId')")
            exec("INSERT INTO match_discounts VALUES ('$matchId', '$first')")
        }
        try {
            val match = Match(id = matchId, fieldId = fieldId, adminId = adminId, dateTime = 1000,
                dateTimeEnd = 2000, maxPlayers = 10, minPlayersRequired = 8,
                matchPrice = BigDecimal("10.00"), genderType = GenderType.MIXED, playerLevel = PlayerLevel.ANY)
            val repository = MatchRepositoryImp()
            fun discounts(): List<String> = transaction(db) {
                exec("SELECT discount_id FROM match_discounts WHERE match_id = '$matchId'") { rows ->
                    buildList { while (rows.next()) add(rows.getObject(1).toString()) }
                }.orEmpty()
            }
            repository.updateMatch(matchId, match)
            assertEquals(listOf(first.toString()), discounts())
            repository.updateMatch(matchId, match.copy(discountIds = listOf(second)))
            assertEquals(listOf(second.toString()), discounts())
            repository.updateMatch(matchId, match.copy(discountIds = emptyList()))
            assertEquals(emptyList(), discounts())
        } finally {
            transaction(db) { exec("DROP ALL OBJECTS") }
        }
    }
}
