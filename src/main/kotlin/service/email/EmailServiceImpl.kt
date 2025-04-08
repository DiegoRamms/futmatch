package service.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailServiceImpl : EmailService {

    override suspend fun sendMfaCodeEmail(to: String, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            println("📩 [TEST] Enviando código MFA al correo: $to")
            println("🔐 Código MFA generado: $code")
            println("⏰ Este código expira en 5 minutos.")
            true
        } catch (e: Exception) {
            println("Error al enviar email MFA: ${e.message}")
            false
        }
    }
}