package service.email

interface EmailService {
    suspend fun sendMfaCodeEmail(to: String, code: String): Boolean
}
