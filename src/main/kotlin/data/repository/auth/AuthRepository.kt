package data.repository.auth

import com.devapplab.model.auth.RefreshTokenPayload
import model.auth.AuthUserSavedData
import model.user.User
import java.util.*

interface AuthRepository {
    fun createUserWithDevice(userWithPasswordHashed: User, deviceInfo: String): AuthUserSavedData
    fun createDevice(userId: UUID, deviceInfo: String, isTrusted: Boolean = false): UUID
    fun completeMfaVerification(userId: UUID, deviceId: UUID, mfaCodeId: UUID)
    fun completeForgotPasswordMfaVerification(mfaCodeId: UUID)
    fun rotateRefreshToken(userId: UUID, deviceId: UUID, newPayload: RefreshTokenPayload)
    fun revokeRefreshToken(deviceId: UUID): Boolean
}
