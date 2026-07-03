import com.devapplab.features.match.validation.CANCEL_MATCH_REASON_MAX_LENGTH
import com.devapplab.features.match.validation.validate
import com.devapplab.model.match.request.CancelMatchRequest
import com.devapplab.utils.StringResourcesKey
import io.ktor.server.plugins.requestvalidation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CancelMatchRequestValidationTest {

    @Test
    fun `blank reason is invalid`() {
        val result = CancelMatchRequest(reason = "   ").validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(listOf(StringResourcesKey.MATCH_CANCEL_REASON_REQUIRED_ERROR.value), invalid.reasons)
    }

    @Test
    fun `reason longer than max length is invalid`() {
        val result = CancelMatchRequest(reason = "a".repeat(CANCEL_MATCH_REASON_MAX_LENGTH + 1)).validate()
        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(listOf(StringResourcesKey.MATCH_CANCEL_REASON_TOO_LONG_ERROR.value), invalid.reasons)
    }

    @Test
    fun `reason at max length after trimming is valid`() {
        val result = CancelMatchRequest(reason = "  " + "a".repeat(CANCEL_MATCH_REASON_MAX_LENGTH) + "  ").validate()
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `valid reason passes`() {
        val result = CancelMatchRequest(reason = "No se completó el número mínimo de jugadores.").validate()
        assertIs<ValidationResult.Valid>(result)
    }
}
