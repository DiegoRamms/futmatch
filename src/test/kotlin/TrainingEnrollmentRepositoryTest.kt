import com.devapplab.data.database.executor.DbExecutor
import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.training.TrainingEnrollmentTable
import com.devapplab.data.database.training.TrainingTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.repository.training.TrainingEnrollmentRepository
import com.devapplab.data.repository.training.TrainingRepository
import com.devapplab.data.repository.training.TrainingSeatResult
import com.devapplab.model.ErrorCode
import com.devapplab.model.payment.PaymentAttemptStatus
import com.devapplab.model.payment.PaymentCaptureMethod
import com.devapplab.model.training.TrainingRequest
import com.devapplab.model.user.Gender
import com.devapplab.model.user.PlayerLevel
import com.devapplab.model.user.PlayerPosition
import com.devapplab.model.user.UserRole
import com.devapplab.model.user.UserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class TrainingEnrollmentRepositoryTest {
    @Test
    fun `one player gets the last spot and concurrent retries reuse the same attempt`() = runBlocking {
        withTraining { _, repo, session ->
            val users = listOf(session.firstPlayer, session.secondPlayer)
            val races = users.map { player ->
                async(Dispatchers.IO) { reserve(repo, session, player) }
            }.awaitAll()
            val winner = races.indexOfFirst { it is TrainingSeatResult.Acquired }
            assertTrue(winner in users.indices)
            assertEquals(TrainingSeatResult.Rejected(ErrorCode.MATCH_FULL), races[1 - winner])
            val firstAttempt = assertIs<TrainingSeatResult.Acquired>(races[winner]).enrollment
            assertEquals(PaymentCaptureMethod.MANUAL, firstAttempt.captureMethod)

            val retries = List(4) { async(Dispatchers.IO) { reserve(repo, session, users[winner]) } }.awaitAll()
            assertEquals(setOf(firstAttempt.id), retries.map { assertIs<TrainingSeatResult.Acquired>(it).enrollment.id }.toSet())
        }
    }

    @Test
    fun `cancellation and provider events release the spot without reviving an old attempt`() = runBlocking {
        withTraining { catalog, repo, session ->
            val acquired = assertIs<TrainingSeatResult.Acquired>(reserve(repo, session, session.firstPlayer)).enrollment
            assertTrue(repo.attach(acquired.id, "pi_training_test"))
            assertTrue(repo.leave(session.training, session.firstPlayer, session.now, 1_000))
            assertEquals(TrainingSeatResult.Rejected(ErrorCode.MATCH_FULL), reserve(repo, session, session.secondPlayer))
            assertTrue(repo.updatePayment("pi_training_test", PaymentAttemptStatus.CANCELED))
            assertFalse(repo.updatePayment("pi_training_test", PaymentAttemptStatus.AUTHORIZED))
            assertEquals(0, catalog.detail(session.training, session.firstPlayer)?.occupied)
            assertIs<TrainingSeatResult.Acquired>(reserve(repo, session, session.secondPlayer))
        }
    }

    @Test
    fun `late departure and rejoin keep the confirmed payment`() = runBlocking {
        withTraining { _, repo, session ->
            val first = assertIs<TrainingSeatResult.Acquired>(reserve(repo, session, session.firstPlayer)).enrollment
            assertTrue(repo.attach(first.id, "pi_reuse_training"))
            assertTrue(repo.updatePayment("pi_reuse_training", PaymentAttemptStatus.AUTHORIZED))
            val window = 8_000_000L
            assertTrue(repo.leave(session.training, session.firstPlayer, session.now, window))
            val next = repo.reserve(session.training, session.firstPlayer, session.now, 9_000_000, window, 300_000)
            assertEquals(first.id, assertIs<TrainingSeatResult.Acquired>(next).enrollment.id)
            assertEquals(PaymentAttemptStatus.AUTHORIZED, next.enrollment.status)
        }
    }

    @Test
    fun `cancelled paid training stays in bookings while refund settles`() = runBlocking {
        withTraining { catalog, repo, session ->
            val attempt = assertIs<TrainingSeatResult.Acquired>(reserve(repo, session, session.firstPlayer)).enrollment
            assertTrue(repo.attach(attempt.id, "pi_training_admin_cancel"))
            assertTrue(repo.updatePayment("pi_training_admin_cancel", PaymentAttemptStatus.SUCCEEDED))
            assertFalse(catalog.cancelPublished(session.training, session.secondPlayer))
            assertTrue(catalog.cancelPublished(session.training, session.admin))

            assertEquals(emptyList(), catalog.list(session.firstPlayer))
            assertEquals(listOf("pi_training_admin_cancel"), repo.dueForRefund())
            assertEquals(PaymentAttemptStatus.SUCCEEDED, catalog.list(session.firstPlayer, booked = true).single().enrollmentStatus)
            assertTrue(repo.updatePayment("pi_training_admin_cancel", PaymentAttemptStatus.REFUNDED))
            assertEquals(PaymentAttemptStatus.REFUNDED, catalog.list(session.firstPlayer, booked = true).single().enrollmentStatus)
            assertEquals(emptyList(), repo.dueForRefund())
        }
    }

    @Test
    fun `the first reservation locks payment terms while allowing edits to the coach`() = runBlocking {
        withTraining { catalog, repo, session ->
            val training = assertNotNull(catalog.detail(session.training, session.admin))
            assertIs<TrainingSeatResult.Acquired>(reserve(repo, session, session.firstPlayer))
            val venue = UUID.fromString(training.fieldId)
            val proposed = TrainingRequest(training.fieldId, training.title, "New coach", training.description,
                training.startsAt, training.endsAt, training.capacity, training.priceInCents)
            assertNull(catalog.save(session.training, session.admin, venue, proposed.copy(priceInCents = 400), isAdmin = true))
            assertNull(catalog.save(session.training, session.secondPlayer, venue, proposed, isAdmin = true))
            val updated = assertNotNull(catalog.save(session.training, session.admin, venue, proposed, isAdmin = true))
            assertEquals("New coach", updated.coachName)
            assertEquals(training.priceInCents, updated.priceInCents)
        }
    }

    private suspend fun reserve(repo: TrainingEnrollmentRepository, session: Session, user: UUID) =
        repo.reserve(session.training, user, session.now, 9_000_000, 1_000, 300_000)

    private suspend fun withTraining(
        test: suspend (TrainingRepository, TrainingEnrollmentRepository, Session) -> Unit
    ) {
        val db = Database.connect("jdbc:h2:mem:training_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        try {
            val executor = object : DbExecutor {
                override suspend fun <T> tx(block: () -> T): T = transaction(db) { block() }
            }
            val (admin, first, second) = transaction(db) {
                SchemaUtils.create(UserTable, LocationsTable, FieldTable, FieldAdminsTable, TrainingTable, TrainingEnrollmentTable)
                Triple(insertUser(UserRole.ADMIN), insertUser(UserRole.PLAYER), insertUser(UserRole.PLAYER))
            }
            val venue = transaction(db) {
                FieldTable.insert {
                    it[name] = "Training venue"
                    it[adminId] = admin
                    it[fieldCost] = BigDecimal("100.00")
                    it[capacity] = 14
                    it[description] = ""
                    it[rules] = ""
                }[FieldTable.id]
            }
            val now = System.currentTimeMillis()
            val catalog = TrainingRepository(executor)
            val created = assertNotNull(catalog.save(null, admin, venue, TrainingRequest(
                venue.toString(), "Training", "Coach", "", now + 7_200_000, now + 10_800_000, 1, 10000
            ), isAdmin = true))
            val training = UUID.fromString(created.id)
            assertTrue(catalog.publish(training, admin))
            test(catalog, TrainingEnrollmentRepository(executor), Session(now, training, admin, first, second))
        } finally {
            transaction(db) { exec("DROP ALL OBJECTS") }
        }
    }

    private fun insertUser(userRole: UserRole): UUID = UserTable.insert {
        it[name] = "Test"
        it[lastName] = "User"
        it[country] = "MX"
        it[birthDate] = 0L
        it[gender] = Gender.MALE
        it[playerPosition] = PlayerPosition.FORWARD
        it[level] = PlayerLevel.ANY
        it[role] = userRole
        it[status] = UserStatus.ACTIVE
    }[UserTable.id]

    private data class Session(val now: Long, val training: UUID, val admin: UUID, val firstPlayer: UUID, val secondPlayer: UUID)
}
