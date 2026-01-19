package service.email

import com.devapplab.model.EmailConfig
import com.devapplab.service.email.getHtmlTemplate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory


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


    override suspend fun sendMfaCodeEmail(to: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val htmlContent = getHtmlTemplate(
            title = "Your MFA Code",
            message = "Use the following code to complete your login. This code will expire in 5 minutes.",
            code = code
        )
        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = "Your MFA Code",
            text = "Your MFA code is: $code. It expires in 5 minutes.",
            html = htmlContent,
            category = "MFA Code"
        )
    }

    override suspend fun sendMfaPasswordResetEmail(to: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val htmlContent = getHtmlTemplate(
            title = "Password Reset",
            message = "You requested a password reset. Use the code below to proceed. This code expires in 10 minutes.",
            code = code
        )
        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = "Password Reset Code",
            text = "Your password reset code is: $code. It expires in 10 minutes.",
            html = htmlContent,
            category = "Password Reset"
        )
    }

    override suspend fun sendRegistrationEmail(to: String, code: String): Boolean = withContext(Dispatchers.IO) {
        val htmlContent = getHtmlTemplate(
            title = "Welcome to Futmatch!",
            message = "Thank you for registering! Please use the code below to verify your account. This code expires in 1 hour.",
            code = code
        )
        sendEmail(
            to = "futmatch1411@gmail.com",
            subject = "Welcome to Futmatch!",
            text = "Welcome! Your registration code is: $code. It expires in 1 hour.",
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
