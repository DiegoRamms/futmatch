import com.devapplab.config.dbQuery
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchInviteDispatchTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.match.MatchInviteRepositoryImpl
import com.devapplab.model.match.MatchInviteNotification
import com.devapplab.model.match.MatchPlayerStatus
import com.devapplab.model.match.MatchStatus
import com.devapplab.model.match.TeamType
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
                MatchPlayersTable, MatchInviteDispatchTable, NotificationTable
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

        val repo = MatchInviteRepositoryImpl()
        val matches = repo.getMatchesForInvite(now, now + 24 * hour)
        assertEquals(listOf(soon), matches.map { it.matchId })
        assertEquals(9, matches.single().availableSpots)
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
    fun `tokens are grouped per user`() = withDb { db ->
        val player = db.user(token = "a")
        transaction(db) { DeviceTable.insert { it[userId] = player; it[fcmToken] = "b" } }
        val tokens = MatchInviteRepositoryImpl().getActiveFcmTokensByUserIds(listOf(player))
        assertEquals(setOf("a", "b"), tokens[player]?.toSet())
    }
}
