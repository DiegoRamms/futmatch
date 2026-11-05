import com.devapplab.config.dbQuery
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.repository.notification.NotificationRepositoryImpl
import com.devapplab.model.notification.NotificationType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class NotificationRepositoryTest {
    @Test
    fun `unread count and bulk read are scoped to user and preserve future notifications`() = runBlocking {
        val database = Database.connect(
            "jdbc:h2:mem:notifications_${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY)")
            exec("INSERT INTO users (id) VALUES ('$userId'), ('$otherUserId')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repository = NotificationRepositoryImpl()
            assertEquals(0L, repository.getUnreadCount(userId))
            assertEquals(0, repository.markAllAsRead(userId))
            suspend fun create(user: UUID) = repository.createNotification(
                user, NotificationType.PAYMENT_SUCCEEDED, "Payment", "Received"
            )
            val alreadyRead = create(userId)
            repository.markAsRead(alreadyRead)
            val unread = create(userId)
            create(userId)
            val otherNotification = create(otherUserId)
            assertEquals(2L, repository.getUnreadCount(userId))
            assertEquals(1L, repository.getUnreadCount(otherUserId))

            assertEquals(2, repository.markAllAsRead(userId))
            assertEquals(0L, repository.getUnreadCount(userId))
            assertEquals(0, repository.markAllAsRead(userId))
            assertTrue(repository.getNotificationById(unread)!!.isRead)
            assertFalse(repository.getNotificationById(otherNotification)!!.isRead)
            assertEquals(1L, repository.getUnreadCount(otherUserId))
            assertEquals(3, repository.getUserNotifications(userId).size)

            val newNotification = create(userId)
            assertFalse(repository.getNotificationById(newNotification)!!.isRead)
            assertEquals(1L, repository.getUnreadCount(userId))
            repository.deleteNotification(newNotification)
            assertEquals(0L, repository.getUnreadCount(userId))
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }
}

class NotificationInboxRepositoryTest {
    @Test
    fun `pagination applies after filtering the selected inbox`() = runBlocking {
        val database = Database.connect("jdbc:h2:mem:notification_pages_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val userId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY)")
            exec("INSERT INTO users (id) VALUES ('$userId')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repo = NotificationRepositoryImpl()
            val first = repo.createNotification(userId, NotificationType.PAYMENT_SUCCEEDED, "First", "Body")
            val second = repo.createNotification(userId, NotificationType.PAYMENT_SUCCEEDED, "Second", "Body")
            val admin = repo.createNotification(userId, NotificationType.PAYMENT_SUCCEEDED, "Admin", "Body", inbox = com.devapplab.model.notification.NotificationInbox.ADMIN)
            transaction(database) {
                exec("UPDATE notifications SET created_at = 100 WHERE id = '$first'")
                exec("UPDATE notifications SET created_at = 200 WHERE id = '$second'")
                exec("UPDATE notifications SET created_at = 300 WHERE id = '$admin'")
            }
            assertEquals(listOf(second), repo.getUserNotifications(userId, limit = 1, offset = 0).map { it.id })
            assertEquals(listOf(first), repo.getUserNotifications(userId, limit = 1, offset = 1).map { it.id })
            assertEquals(listOf(admin), repo.getUserNotifications(userId, inbox = com.devapplab.model.notification.NotificationInbox.ADMIN).map { it.id })
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    @Test
    fun `inboxes isolate history unread count and bulk read for each user`() = runBlocking {
        val database = Database.connect("jdbc:h2:mem:inboxes_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val user = UUID.randomUUID()
        val other = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY, \"role\" VARCHAR(32), status VARCHAR(32))")
            exec("INSERT INTO users (id, \"role\", status) VALUES ('$user', 'ADMIN', 'ACTIVE'), ('$other', 'PLAYER', 'ACTIVE')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repo = NotificationRepositoryImpl()
            suspend fun add(owner: UUID, inbox: com.devapplab.model.notification.NotificationInbox) =
                repo.createNotification(owner, NotificationType.PAYMENT_SUCCEEDED, "Title", "Body", inbox = inbox)
            val player = add(user, com.devapplab.model.notification.NotificationInbox.PLAYER)
            val admin = add(user, com.devapplab.model.notification.NotificationInbox.ADMIN)
            add(other, com.devapplab.model.notification.NotificationInbox.ADMIN)
            assertEquals(listOf(player), repo.getUserNotifications(user).map { it.id })
            assertEquals(listOf(admin), repo.getUserNotifications(user, inbox = com.devapplab.model.notification.NotificationInbox.ADMIN).map { it.id })
            assertEquals(1L, repo.getUnreadCount(user))
            assertEquals(1, repo.markAllAsRead(user, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertEquals(1L, repo.getUnreadCount(user))
            assertEquals(0L, repo.getUnreadCount(user, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertEquals(1L, repo.getUnreadCount(other, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertTrue(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertFalse(repo.canAccessInbox(other, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertFalse(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ORGANIZER))
            transaction(database) { exec("UPDATE users SET \"role\" = 'PLAYER' WHERE id = '$user'") }
            assertFalse(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertTrue(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.PLAYER))
            transaction(database) { exec("UPDATE users SET \"role\" = 'ORGANIZER' WHERE id = '$user'") }
            assertTrue(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ORGANIZER))
            assertFalse(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ADMIN))
            transaction(database) { exec("UPDATE users SET status = 'SUSPENDED' WHERE id = '$user'") }
            assertFalse(repo.canAccessInbox(user, com.devapplab.model.notification.NotificationInbox.ORGANIZER))
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }
}

class PaymentWarningPersistenceTest {
    @Test
    fun `cancelled match is not marked and receives no warning`() = runBlocking {
        val database = Database.connect("jdbc:h2:mem:cancelled_warning_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val matchId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY)")
            exec("CREATE TABLE matches (id UUID PRIMARY KEY, status VARCHAR(20), payment_window_warning_sent_at BIGINT)")
            exec("INSERT INTO users (id) VALUES ('$userId')")
            exec("INSERT INTO matches (id, status) VALUES ('$matchId', 'CANCELED')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repository = NotificationRepositoryImpl()
            val item = com.devapplab.data.repository.notification.PaymentWarningNotification(
                userId, "Title", "Body", "{}", com.devapplab.model.notification.NotificationInbox.ADMIN
            )
            assertFalse(dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item), 123L) })
            assertEquals(0L, repository.getUnreadCount(userId, com.devapplab.model.notification.NotificationInbox.ADMIN))
            transaction(database) { exec("UPDATE matches SET status = 'SCHEDULED' WHERE id = '$matchId'") }
            assertTrue(dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item), 124L) })
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    @Test
    fun `simultaneous attempts save only one notification per recipient`() = runBlocking {
        val database = Database.connect("jdbc:h2:mem:warning_race_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", driver = "org.h2.Driver")
        val matchId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY)")
            exec("CREATE TABLE matches (id UUID PRIMARY KEY, status VARCHAR(20), payment_window_warning_sent_at BIGINT)")
            exec("INSERT INTO users (id) VALUES ('$userId')")
            exec("INSERT INTO matches (id, status) VALUES ('$matchId', 'SCHEDULED')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repository = NotificationRepositoryImpl()
            val item = com.devapplab.data.repository.notification.PaymentWarningNotification(
                userId, "Title", "Body", "{}", com.devapplab.model.notification.NotificationInbox.ADMIN
            )
            val start = CompletableDeferred<Unit>()
            val attempts = List(2) {
                async(Dispatchers.IO) {
                    start.await()
                    dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item), 123L) }
                }
            }
            start.complete(Unit)
            assertEquals(listOf(false, true), attempts.map { it.await() }.sortedBy { it })
            assertEquals(1L, repository.getUnreadCount(userId, com.devapplab.model.notification.NotificationInbox.ADMIN))
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }

    @Test
    fun `all recipients and completion marker commit together and retry is idempotent`() = runBlocking {
        val database = Database.connect("jdbc:h2:mem:warnings_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        val matchId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        transaction(database) {
            exec("CREATE TABLE users (id UUID PRIMARY KEY)")
            exec("CREATE TABLE matches (id UUID PRIMARY KEY, status VARCHAR(20), payment_window_warning_sent_at BIGINT)")
            exec("INSERT INTO users (id) VALUES ('$userId')")
            exec("INSERT INTO matches (id, status) VALUES ('$matchId', 'SCHEDULED')")
            SchemaUtils.create(NotificationTable)
        }
        try {
            val repository = NotificationRepositoryImpl()
            fun item(id: UUID) = com.devapplab.data.repository.notification.PaymentWarningNotification(
                id, "Title", "Body", "{}", com.devapplab.model.notification.NotificationInbox.ADMIN
            )
            val attempt = runCatching { dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item(userId), item(otherId)), 123L) } }
            assertTrue(attempt.isFailure)
            assertEquals(0L, repository.getUnreadCount(userId, com.devapplab.model.notification.NotificationInbox.ADMIN))
            transaction(database) { exec("INSERT INTO users (id) VALUES ('$otherId')") }
            assertTrue(dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item(userId), item(otherId)), 123L) })
            assertFalse(dbQuery { repository.savePaymentWarningsTx(matchId, listOf(item(userId), item(otherId)), 124L) })
            assertEquals(1L, repository.getUnreadCount(userId, com.devapplab.model.notification.NotificationInbox.ADMIN))
            assertEquals(1L, repository.getUnreadCount(otherId, com.devapplab.model.notification.NotificationInbox.ADMIN))
        } finally {
            transaction(database) { exec("DROP ALL OBJECTS") }
        }
    }
}
