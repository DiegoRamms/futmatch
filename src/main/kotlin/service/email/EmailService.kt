package service.email

import java.util.Locale

interface EmailService {
    suspend fun sendMfaCodeEmail(to: String, code: String, locale: Locale): Boolean
    suspend fun sendMfaPasswordResetEmail(to: String, code: String, locale: Locale): Boolean
    suspend fun sendRegistrationEmail(to: String, code: String, locale: Locale): Boolean
}
