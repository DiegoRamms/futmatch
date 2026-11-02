import com.devapplab.model.match.GenderType
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.response.MatchBestPlayerResponse
import com.devapplab.model.match.response.MatchDetailResponse
import com.devapplab.model.match.response.MatchGoalBreakdownResponse
import com.devapplab.model.match.response.MatchOrganizerResponse
import com.devapplab.model.match.response.MatchSummaryResponse
import com.devapplab.model.match.response.PlayerGoalResponse
import com.devapplab.model.match.response.PlayerSummary
import com.devapplab.model.match.response.TeamGoalBreakdownResponse
import com.devapplab.model.match.response.TeamPlayersSummary
import com.devapplab.model.match.response.TeamSummaryResponse
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.service.match.GuestMatchSanitizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestMatchSanitizerTest {

    private val playerId = UUID.randomUUID()
    private val playerName = "Jugador Privado"
    private val avatarUrl = "https://cdn.example.com/avatar-private.jpg"
    private val organizerId = UUID.randomUUID()
    private val organizerLastName = "Organizadora"

    private fun teams() = TeamSummaryResponse(
        teamA = TeamPlayersSummary(
            playerCount = 2,
            players = listOf(
                PlayerSummary(playerId, avatarUrl, Gender.MALE, playerName, country = "MX"),
                PlayerSummary(UUID.randomUUID(), null, Gender.FEMALE, "Otra Persona")
            )
        ),
        teamB = TeamPlayersSummary(
            playerCount = 1,
            players = listOf(PlayerSummary(UUID.randomUUID(), null, Gender.MALE, "Tercero"))
        )
    )

    private fun summary() = MatchSummaryResponse(
        id = UUID.randomUUID(),
        fieldName = "Cancha Norte",
        startTime = 1_000L,
        endTime = 2_000L,
        originalPriceInCents = 10_000,
        totalDiscountInCents = 0,
        priceInCents = 10_000,
        genderType = GenderType.MIXED,
        playerLevel = PlayerLevel.ANY,
        status = MatchStatus.SCHEDULED,
        maxPlayers = 14,
        availableSpots = 11,
        teams = teams(),
        location = null
    )

    private fun detail() = MatchDetailResponse(
        id = UUID.randomUUID(),
        fieldName = "Cancha Norte",
        startTime = 1_000L,
        endTime = 2_000L,
        originalPriceInCents = 10_000,
        totalDiscountInCents = 0,
        priceInCents = 10_000,
        genderType = GenderType.MIXED,
        status = MatchStatus.SCHEDULED,
        goalBreakdown = MatchGoalBreakdownResponse(
            teamA = TeamGoalBreakdownResponse(listOf(PlayerGoalResponse(playerId, playerName, 2)), 0),
            teamB = TeamGoalBreakdownResponse(emptyList(), 1)
        ),
        bestPlayer = MatchBestPlayerResponse(playerId, playerName),
        maxPlayers = 14,
        availableSpots = 11,
        teams = teams(),
        location = null,
        footwearType = null,
        fieldType = null,
        hasParking = true,
        extraInfo = null,
        description = "Partido amistoso",
        rules = "Fair play",
        organizer = MatchOrganizerResponse(organizerId, "Ana", organizerLastName, avatarUrl)
    )

    @Test
    fun `summary keeps availability and drops every player`() {
        val original = summary()
        val sanitized = GuestMatchSanitizer.sanitize(original)

        assertTrue(sanitized.teams.teamA.players.isEmpty())
        assertTrue(sanitized.teams.teamB.players.isEmpty())
        assertEquals(2, sanitized.teams.teamA.playerCount)
        assertEquals(1, sanitized.teams.teamB.playerCount)
        assertEquals(original.availableSpots, sanitized.availableSpots)
        assertEquals(original.copy(teams = sanitized.teams), sanitized)
    }

    @Test
    fun `summary sanitizing does not mutate the cached original`() {
        val original = summary()
        GuestMatchSanitizer.sanitize(original)

        assertEquals(2, original.teams.teamA.players.size)
    }

    @Test
    fun `detail drops players, scorers, best player and organizer`() {
        val sanitized = GuestMatchSanitizer.sanitize(detail())

        assertTrue(sanitized.teams.teamA.players.isEmpty())
        assertTrue(sanitized.teams.teamB.players.isEmpty())
        assertEquals(2, sanitized.teams.teamA.playerCount)
        assertNull(sanitized.goalBreakdown)
        assertNull(sanitized.bestPlayer)
        assertNull(sanitized.organizer)
        assertEquals("Partido amistoso", sanitized.description)
    }

    @Test
    fun `serialized guest responses contain no player identifiers`() {
        val json = Json.encodeToString(GuestMatchSanitizer.sanitize(summary())) +
            Json.encodeToString(GuestMatchSanitizer.sanitize(detail()))

        assertFalse(json.contains(playerName))
        assertFalse(json.contains(playerId.toString()))
        assertFalse(json.contains(avatarUrl))
        assertFalse(json.contains(organizerId.toString()))
        assertFalse(json.contains(organizerLastName))
    }
}
