package com.devapplab.service.email

import com.devapplab.model.EmailConfig
import com.devapplab.utils.StringResourcesKey
import com.devapplab.utils.getString
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Locale


/**
 * This implementation is intended for testing purposes only.
 * It sends emails using Mailtrap's sandbox environment.
 *
 * TODO: When moving to production, this implementation should be replaced
 * with a real email service provider that sends emails to the actual recipients.
 */
class RealEmailServiceTestImp(
    private val client: HttpClient,
    private val emailConfig: EmailConfig
) : EmailService {

    private val logger = LoggerFactory.getLogger(this::class.java)


    override suspend fun sendMfaCodeEmail(to: String, code: String, locale: Locale): Boolean = withContext(Dispatchers.IO) {
        val title = locale.getString(StringResourcesKey.EMAIL_MFA_TITLE)
        val message = locale.getString(StringResourcesKey.EMAIL_MFA_MESSAGE)
        val subject = locale.getString(StringResourcesKey.EMAIL_MFA_SUBJECT)
        val footerText = locale.getString(StringResourcesKey.EMAIL_FOOTER_TEXT)

        val htmlContent = getHtmlTemplate(
            title = title,
            message = message,
            code = code,
            footerText = footerText
        )

        logCode(code)

        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = subject,
            text = "$message $code",
            html = htmlContent,
            category = "MFA Code"
        )
    }

    override suspend fun sendMfaPasswordResetEmail(to: String, code: String, locale: Locale): Boolean = withContext(Dispatchers.IO) {
        val title = locale.getString(StringResourcesKey.EMAIL_PASSWORD_RESET_TITLE)
        val message = locale.getString(StringResourcesKey.EMAIL_PASSWORD_RESET_MESSAGE)
        val subject = locale.getString(StringResourcesKey.EMAIL_PASSWORD_RESET_SUBJECT)
        val footerText = locale.getString(StringResourcesKey.EMAIL_FOOTER_TEXT)

        val htmlContent = getHtmlTemplate(
            title = title,
            message = message,
            code = code,
            footerText = footerText
        )

       logCode(code)

        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = subject,
            text = "$message $code",
            html = htmlContent,
            category = "Password Reset"
        )
    }

    override suspend fun sendRegistrationEmail(to: String, code: String, locale: Locale): Boolean = withContext(Dispatchers.IO) {
        val title = locale.getString(StringResourcesKey.EMAIL_REGISTRATION_TITLE)
        val message = locale.getString(StringResourcesKey.EMAIL_REGISTRATION_MESSAGE)
        val subject = locale.getString(StringResourcesKey.EMAIL_REGISTRATION_SUBJECT)
        val footerText = locale.getString(StringResourcesKey.EMAIL_FOOTER_TEXT)

        val htmlContent = getHtmlTemplate(
            title = title,
            message = message,
            code = code,
            footerText = footerText
        )

        logCode(code)
        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = subject,
            text = "$message $code",
            html = htmlContent,
            category = "Registration"
        )
    }

    private suspend fun sendEmail(to: String, subject: String, text: String, html: String, category: String): Boolean {
        return try {
            val requestBody = MailtrapRequest(
                from = EmailAddress(emailConfig.fromEmail, emailConfig.fromName),
                to = listOf(EmailAddress(to)),
                subject = subject,
                text = text,
                html = html,
                category = category
            )

            val response = client.post("https://send.api.mailtrap.io/api/send") {
                header(HttpHeaders.Authorization, "Bearer ${emailConfig.apiToken}")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                logger.info("Email sent to $to. Status: ${response.status}")
                true
            } else {
                logger.error("Failed to send email to $to. Status: ${response.status}. Body: ${response.body<String>()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to send email to $to: ${e.message}", e)
            false
        }
    }


    private fun logCode(code: String){
        logger.info("🔐 Código MFA generado: $code")
        logger.info("⏰ Este código expira en 5 minutos.")
    }


    @Serializable
    data class MailtrapRequest(
        val from: EmailAddress,
        val to: List<EmailAddress>,
        val subject: String,
        val text: String,
        val html: String,
        val category: String
    )

    @Serializable
    data class EmailAddress(
        val email: String,
        val name: String? = null
    )
}
