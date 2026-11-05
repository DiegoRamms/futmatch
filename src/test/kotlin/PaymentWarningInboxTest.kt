import com.devapplab.model.notification.NotificationInbox
import com.devapplab.model.user.UserRole
import com.devapplab.service.match.paymentWarningInbox
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentWarningInboxTest {
    @Test
    fun `warning uses recipient role and preserves fallback for other supervisors`() {
        assertEquals(NotificationInbox.ADMIN, paymentWarningInbox(true, UserRole.ORGANIZER))
        assertEquals(NotificationInbox.ADMIN, paymentWarningInbox(false, UserRole.ADMIN))
        assertEquals(NotificationInbox.ORGANIZER, paymentWarningInbox(false, UserRole.ORGANIZER))
        assertEquals(NotificationInbox.PLAYER, paymentWarningInbox(false, UserRole.PLAYER))
        assertEquals(NotificationInbox.PLAYER, paymentWarningInbox(false, null))
    }
}
