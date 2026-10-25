import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.repository.notification.NotificationRepositoryImpl
import com.devapplab.model.notification.NotificationType
import kotlinx.coroutines.runBlocking
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
