package com.devapplab.service.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Locale

class EmailServiceImpl : EmailService {

    override suspend fun sendMfaCodeEmail(to: String, code: String, locale: Locale): Boolean =
        withContext(Dispatchers.IO) {
            val logger = LoggerFactory.getLogger(this::class.java)
            try {
                logger.info("📩 [TEST] Enviando código MFA al correo: $to")
                logger.info("Código MFA generado para envío por email.")
                true
            } catch (e: Exception) {
                logger.error("Error al enviar email MFA: ${e.message}")
                false
            }
        }

    override suspend fun sendMfaPasswordResetEmail(to: String, code: String, locale: Locale): Boolean =
        withContext(Dispatchers.IO) {
            val logger = LoggerFactory.getLogger(this::class.java)
            try {
                logger.info("📩 [TEST] Enviando código de reseteo de contraseña al correo: $to")
                logger.info("Código de reseteo generado para envío por email.")
                true
            } catch (e: Exception) {
                logger.error("Error al enviar email de reseteo de contraseña: ${e.message}")
                false
            }
        }

    override suspend fun sendRegistrationEmail(to: String, code: String, locale: Locale): Boolean =
        withContext(Dispatchers.IO) {
            val logger = LoggerFactory.getLogger(this::class.java)
            try {
                logger.info("📩 [TEST] Enviando código de registro al correo: $to")
                logger.info("Código de registro generado para envío por email.")
                true
            } catch (e: Exception) {
                logger.error("Error al enviar email de registro: ${e.message}")
                false
            }
        }

    override suspend fun sendPasswordChangedEmail(to: String, locale: Locale): Boolean =
        withContext(Dispatchers.IO) {
            val logger = LoggerFactory.getLogger(this::class.java)
            try {
                logger.info("📩 [TEST] Enviando notificación de contraseña actualizada al correo: $to")
                true
            } catch (e: Exception) {
                logger.error("Error al enviar email de contraseña actualizada: ${e.message}")
                false
            }
        }
}
