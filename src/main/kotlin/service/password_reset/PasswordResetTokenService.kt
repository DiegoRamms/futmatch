import com.devapplab.data.repository.password_reset.PasswordResetTokenRepository
import com.devapplab.model.AppResult
import com.devapplab.service.hashing.HashingService
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.createError
import model.auth.AuthUserSavedData
import java.security.SecureRandom
import java.util.*

interface PasswordResetTokenService {
    suspend fun createAndSaveResetToken(user: AuthUserSavedData): String
    suspend fun verifyResetToken(token: String, locale: Locale): AppResult<UUID>
    suspend fun invalidateToken(token: String)
}

class PasswordResetTokenServiceImpl(
    private val repository: PasswordResetTokenRepository,
    private val hashingService: HashingService
) : PasswordResetTokenService {

    private val secureRandom = SecureRandom()

    override suspend fun createAndSaveResetToken(user: AuthUserSavedData): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        val hashedToken = hashingService.hashOpaqueToken(plainToken)
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes

        repository.create(hashedToken, user.userId, expiresAt)
        return plainToken
    }

    override suspend fun verifyResetToken(token: String, locale: Locale): AppResult<UUID> {
        val hashedInputToken = hashingService.hashOpaqueToken(token)
        val record = repository.findByToken(hashedInputToken)

        return when {
            record == null -> locale.createError(
                titleKey = StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_TITLE,
                descriptionKey = StringResourcesKey.PASSWORD_RESET_TOKEN_INVALID_DESCRIPTION
            )
            record.expiresAt < System.currentTimeMillis() -> {
                repository.delete(hashedInputToken) // Delete expired token
                locale.createError(
                    titleKey = StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_TITLE,
                    descriptionKey = StringResourcesKey.PASSWORD_RESET_TOKEN_EXPIRED_DESCRIPTION
                )
            }
            else -> AppResult.Success(record.userId)
        }
    }

    override suspend fun invalidateToken(token: String) {
        val hashedInputToken = hashingService.hashOpaqueToken(token)
        repository.delete(hashedInputToken)
    }
}
