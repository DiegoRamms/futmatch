package service.email

interface EmailService {
    suspend fun sendMfaCodeEmail(to: String, code: String): Boolean
    suspend fun sendMfaPasswordResetEmail(to: String, code: String): Boolean
    suspend fun sendRegistrationEmail(to: String, code: String): Boolean
}
