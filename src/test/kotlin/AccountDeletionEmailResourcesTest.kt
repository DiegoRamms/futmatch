import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.getString
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDeletionEmailResourcesTest {

    @Test
    fun `Spanish account-deleted email copy confirms deletion and legal retention`() {
        assertEquals(
            "Tu cuenta de FutMatch fue eliminada",
            Locale("es", "MX").getString(StringResourcesKey.EMAIL_ACCOUNT_DELETED_SUBJECT)
        )
        val message = Locale("es", "MX").getString(StringResourcesKey.EMAIL_ACCOUNT_DELETED_MESSAGE)
        assertTrue(message.contains("fue eliminada correctamente"))
        assertTrue(message.contains("obligaciones legales"))
    }

    @Test
    fun `English account-deleted email copy confirms deletion and legal retention`() {
        assertEquals(
            "Your Futmatch Account Was Deleted",
            Locale.US.getString(StringResourcesKey.EMAIL_ACCOUNT_DELETED_SUBJECT)
        )
        val message = Locale.US.getString(StringResourcesKey.EMAIL_ACCOUNT_DELETED_MESSAGE)
        assertTrue(message.contains("deleted successfully"))
        assertTrue(message.contains("required by law"))
    }
}
