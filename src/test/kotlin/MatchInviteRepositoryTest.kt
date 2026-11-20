import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchInviteDispatchTable
import com.devapplab.data.database.match.MatchPlayerGoalsTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchResultsTable
import com.devapplab.data.database.match.MatchWhatsAppGuestsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.match.MatchTeamsTable
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.match.MatchInviteRepositoryImpl
import com.devapplab.data.repository.match.MatchRepositoryImp
import com.devapplab.model.firestore.toRealtimePlayerList
import com.devapplab.model.match.MatchInviteNotification
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchSeatChange
import com.devapplab.model.match.TeamGoalInput
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.TeamType
import com.devapplab.model.match.mapper.toMatchDetailResponse
import com.devapplab.model.user.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchInviteRepositoryTest {
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    private fun withDb(extraUrl: String = "", block: suspend (Database) -> Unit) = runBlocking {
        val database = Database.connect(
            "jdbc:h2:mem:match_invites_${UUID.randomUUID()};DB_CLOSE_DELAY=-1$extraUrl",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(
                UserTable, DeviceTable, LocationsTable, FieldTable, MatchTable,
                MatchPlayersTable, MatchInviteDispatchTable, NotificationTable,
                MatchWhatsAppGuestsTable, MatchResultsTable, MatchPlayerGoalsTable,
                MatchTeamsTable, FieldImagesTable, DiscountsTable, MatchDiscountsTable
            )
        }
        try {
            block(database)
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    private fun Database.user(
        role: UserRole = UserRole.PLAYER,
        status: UserStatus = UserStatus.ACTIVE,
        invitesEnabled: Boolean = true,
        token: String? = "token-${UUID.randomUUID()}",
        deviceActive: Boolean = true,
    ): UUID = transaction(this) {
        val id = UserTable.insert {
            it[name] = "Test"
            it[lastName] = "User"
            it[UserTable.status] = status
            it[country] = "MX"
            it[birthDate] = 0L
            it[gender] = Gender.MALE
            it[playerPosition] = PlayerPosition.FORWARD
            it[level] = PlayerLevel.ANY
            it[UserTable.role] = role
            it[matchInviteNotificationsEnabled] = invitesEnabled
        }[UserTable.id]
        DeviceTable.insert {
            it[userId] = id
            it[fcmToken] = token
            it[isActive] = deviceActive
        }
        id
    }

    private fun Database.match(adminId: UUID, startsInHours: Long, maxPlayers: Int = 10, status: MatchStatus = MatchStatus.SCHEDULED): UUID =
        transaction(this) {
            val fieldId = FieldTable.insert {
                it[name] = "Field ${UUID.randomUUID()}"
                it[fieldCost] = BigDecimal("100.00")
                it[capacity] = maxPlayers
                it[FieldTable.adminId] = adminId
                it[description] = ""
                it[rules] = ""
            }[FieldTable.id]
            MatchTable.insert {
                it[MatchTable.fieldId] = fieldId
                it[MatchTable.adminId] = adminId
                it[dateTime] = now + startsInHours * hour
                it[dateTimeEnd] = now + (startsInHours + 1) * hour
                it[MatchTable.maxPlayers] = maxPlayers
                it[minPlayersRequired] = 2
                it[matchPrice] = BigDecimal("50.00")
                it[MatchTable.status] = status
            }[MatchTable.id]
        }

    private fun Database.join(matchId: UUID, userId: UUID, status: MatchPlayerStatus = MatchPlayerStatus.JOINED) = transaction(this) {
        MatchPlayersTable.insert {
            it[MatchPlayersTable.matchId] = matchId
            it[MatchPlayersTable.userId] = userId
            it[team] = TeamType.A
            it[MatchPlayersTable.status] = status
        }
    }

    private fun invite(userId: UUID) = MatchInviteNotification(userId, "Title", "Body", "{}")

    @Test
    fun `candidates are active opted-in players with an active push token`() = withDb { db ->
        val eligible = db.user()
        db.user(invitesEnabled = false)
        db.user(token = null)
        db.user(deviceActive = false)
        db.user(role = UserRole.ADMIN)
        db.user(status = UserStatus.BLOCKED)

        val candidates = MatchInviteRepositoryImpl().getInviteCandidates()
        assertEquals(listOf(eligible), candidates.map { it.userId })
        assertEquals("es-MX", candidates.single().localeTag)
    }

    @Test
    fun `upcoming matches report enrolled players and active player ids`() = withDb { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val joined = db.user()
        val left = db.user()
        val soon = db.match(admin, startsInHours = 5)
        db.match(admin, startsInHours = 30)
        db.match(admin, startsInHours = 3, status = MatchStatus.CANCELED)
        db.join(soon, joined)
        db.join(soon, left, MatchPlayerStatus.LEFT)
        assertEquals(MatchSeatChange.SUCCESS, MatchRepositoryImp().addWhatsAppGuest(soon, "Invitado", TeamType.B))

        val repo = MatchInviteRepositoryImpl()
        val matches = repo.getMatchesForInvite(now, now + 24 * hour)
        assertEquals(listOf(soon), matches.map { it.matchId })
        assertEquals(8, matches.single().availableSpots)
        assertEquals(mapOf(soon to setOf(joined)), repo.getActivePlayerIds(listOf(soon)))
    }

    @Test
    fun `saving invites stores milestones once and feeds the daily cap`() = withDb { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val player = db.user()
        val matchId = db.match(admin, startsInHours = 5)
        val repo = MatchInviteRepositoryImpl()

        assertTrue(dbQuery { repo.saveMatchInvitesTx(matchId, setOf(24, 6), listOf(invite(player)), now) })
        assertFalse(dbQuery { repo.saveMatchInvitesTx(matchId, setOf(6), listOf(invite(player)), now) })

        assertEquals(mapOf(matchId to setOf(24, 6)), repo.getDispatchedMilestones(listOf(matchId)))
        assertEquals(setOf(player), repo.getUsersInvitedSince(now - 24 * hour))
        assertTrue(repo.getUsersInvitedSince(now + 1).isEmpty())
        assertEquals(1L, transaction(db) { NotificationTable.selectAll().count() })
    }

    @Test
    fun `full or canceled matches are not saved`() = withDb { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val player = db.user()
        val other = db.user()
        val full = db.match(admin, startsInHours = 5, maxPlayers = 1)
        db.join(full, other)
        val repo = MatchInviteRepositoryImpl()

        assertFalse(dbQuery { repo.saveMatchInvitesTx(full, setOf(6), listOf(invite(player)), now) })

        transaction(db) { MatchTable.update({ MatchTable.id eq full }) { it[status] = MatchStatus.CANCELED } }
        assertFalse(dbQuery { repo.saveMatchInvitesTx(full, setOf(6), emptyList(), now) })
        assertTrue(repo.getDispatchedMilestones(listOf(full)).isEmpty())
    }

    @Test
    fun `simultaneous runs send a milestone only once`() = withDb(";LOCK_TIMEOUT=10000") { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val player = db.user()
        val matchId = db.match(admin, startsInHours = 5)
        val repo = MatchInviteRepositoryImpl()

        val start = CompletableDeferred<Unit>()
        val results = coroutineScope {
            val attempts = List(2) {
                async(Dispatchers.IO) {
                    start.await()
                    dbQuery { repo.saveMatchInvitesTx(matchId, setOf(6), listOf(invite(player)), now) }
                }
            }
            start.complete(Unit)
            attempts.map { it.await() }
        }
        assertEquals(listOf(false, true), results.sorted())
        assertEquals(1L, transaction(db) { NotificationTable.selectAll().count() })
    }

    @Test
    fun `guest and app reservation serialize on last team seat`() = withDb(";LOCK_TIMEOUT=10000") { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val player = db.user()
        val matchId = db.match(admin, startsInHours = 5, maxPlayers = 2)
        val repository = MatchRepositoryImp()
        val start = CompletableDeferred<Unit>()
        val attempts = coroutineScope {
            val appJoin = async(Dispatchers.IO) {
                start.await()
                repository.addPlayerToMatch(matchId, player, TeamType.A)
            }
            val guestJoin = async(Dispatchers.IO) {
                start.await()
                repository.addWhatsAppGuest(matchId, "Invitado", TeamType.A)
            }
            start.complete(Unit)
            listOf(appJoin.await(), guestJoin.await())
        }
        assertEquals(setOf(MatchSeatChange.SUCCESS, MatchSeatChange.TEAM_FULL), attempts.toSet())
        assertEquals(1L, transaction(db) {
            MatchPlayersTable.selectAll().count() + MatchWhatsAppGuestsTable.selectAll().count()
        })
    }

    @Test
    fun `removing a named guest frees the seat and a late app payment cannot displace a guest`() = withDb { db ->
        val matchId = db.match(db.user(role = UserRole.ADMIN), startsInHours = 5, maxPlayers = 2)
        val player = db.user()
        val repository = MatchRepositoryImp()
        assertEquals(MatchSeatChange.SUCCESS, repository.addPlayerToMatch(matchId, player, TeamType.A))
        val playerId = checkNotNull(repository.getMatchPlayerId(matchId, player))
        assertTrue(repository.removePlayerFromMatch(matchId, player))
        assertEquals(MatchSeatChange.SUCCESS, repository.addWhatsAppGuest(matchId, "Invitado", TeamType.A))
        assertFalse(repository.updatePlayerStatus(playerId, MatchPlayerStatus.JOINED))

        val guest = transaction(db) { MatchWhatsAppGuestsTable.selectAll().single() }
        assertEquals("Invitado", guest[MatchWhatsAppGuestsTable.name])
        val otherMatch = db.match(db.user(role = UserRole.ADMIN), startsInHours = 5)
        val guestId = guest[MatchWhatsAppGuestsTable.id]
        assertEquals(MatchSeatChange.GUEST_NOT_FOUND, repository.removeWhatsAppGuest(otherMatch, guestId))
        assertEquals(MatchSeatChange.SUCCESS, repository.removeWhatsAppGuest(matchId, guestId))
        assertEquals(MatchSeatChange.SUCCESS, repository.addPlayerToMatch(matchId, player, TeamType.A))
    }

    @Test
    fun `guest-only draw freezes named guests and stores no MVP`() = withDb { db ->
        val admin = db.user(role = UserRole.ADMIN)
        val matchId = db.match(admin, startsInHours = 5)
        val mixed = db.match(admin, startsInHours = 5)
        val repository = MatchRepositoryImp()
        assertEquals(MatchSeatChange.SUCCESS, repository.addWhatsAppGuest(matchId, "Invitado A", TeamType.A))
        assertEquals(MatchSeatChange.SUCCESS, repository.addWhatsAppGuest(mixed, "Invitado B", TeamType.B))
        db.join(mixed, db.user())
        transaction(db) {
            MatchTable.update({ MatchTable.id eq matchId }) { it[status] = MatchStatus.PENDING_RESULT }
            MatchTable.update({ MatchTable.id eq mixed }) { it[status] = MatchStatus.PENDING_RESULT }
        }
        val active = assertNotNull(repository.getMatchById(matchId))
        assertTrue(active.toMatchDetailResponse().whatsappGuests.isEmpty())
        assertEquals(1, active.toMatchDetailResponse().teams.teamA.whatsappGuestCount)
        val realtime = active.toRealtimePlayerList(300_000) { _, avatar -> avatar }
        assertEquals(listOf("Invitado A"), realtime.whatsappGuests.map { it.name })
        assertTrue(realtime.players.isEmpty())
        assertEquals(
            listOf(mapOf("id" to active.whatsappGuests.single().id.toString(), "name" to "Invitado A", "team" to "A")),
            realtime.toMap()["whatsappGuests"]
        )
        val canceled = active.copy(status = MatchStatus.CANCELED)
        assertEquals(active.whatsappGuests, canceled.toMatchDetailResponse().whatsappGuests)
        assertTrue(canceled.toRealtimePlayerList(300_000) { _, avatar -> avatar }.whatsappGuests.isEmpty())

        val score = listOf(TeamGoalInput(TeamType.A, 0), TeamGoalInput(TeamType.B, 0))
        assertNull(repository.completeMatchAtomic(mixed, null, emptyList(), score, emptySet()))
        assertEquals(0 to 0, repository.completeMatchAtomic(matchId, null, emptyList(), score, emptySet()))
        val completed = assertNotNull(repository.getMatchById(matchId))
        assertEquals(active.whatsappGuests, completed.toMatchDetailResponse().whatsappGuests)
        assertTrue(completed.toRealtimePlayerList(300_000) { _, avatar -> avatar }.whatsappGuests.isEmpty())
        transaction(db) {
            val result = MatchResultsTable.selectAll().single()
            assertNull(result[MatchResultsTable.bestPlayerId])
            assertEquals(0L, MatchPlayerGoalsTable.selectAll().count())
            val guest = MatchWhatsAppGuestsTable.selectAll().where { MatchWhatsAppGuestsTable.matchId eq matchId }.single()
            assertEquals("Invitado A", guest[MatchWhatsAppGuestsTable.name])
        }
        val guestId = transaction(db) {
            MatchWhatsAppGuestsTable.selectAll().where { MatchWhatsAppGuestsTable.matchId eq matchId }
                .single()[MatchWhatsAppGuestsTable.id]
        }
        assertEquals(MatchSeatChange.NOT_SCHEDULED, repository.removeWhatsAppGuest(matchId, guestId))
    }

    @Test
    fun `tokens are grouped per user`() = withDb { db ->
        val player = db.user(token = "a")
        transaction(db) { DeviceTable.insert { it[userId] = player; it[fcmToken] = "b" } }
        val tokens = MatchInviteRepositoryImpl().getActiveFcmTokensByUserIds(listOf(player))
        assertEquals(setOf("a", "b"), tokens[player]?.toSet())
    }
}
