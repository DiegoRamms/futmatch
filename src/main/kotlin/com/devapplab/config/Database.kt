package com.devapplab.config

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.discount.UserMatchDiscountsTable
import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.login_attempt.LoginAttemptTable
import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchResultsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.mfa.MfaCodeTable
import com.devapplab.data.database.password_reset.PasswordResetTokensTable
import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.data.database.payments.StripeWebhookEventsTable
import com.devapplab.data.database.pending_registrations.PendingRegistrationTable
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.data.database.user.UserPaymentProfileTable
import com.devapplab.data.database.user.UserTable
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

fun Application.configureDatabase() {
    val database = Database.connect(
        url = environment.config.propertyOrNull("database.url")?.getString() ?: "",
        driver = environment.config.propertyOrNull("database.driver")?.getString() ?: "",
        user = environment.config.propertyOrNull("database.user")?.getString() ?: "",
        password = environment.config.propertyOrNull("database.password")?.getString() ?: ""
    )

    val isDevelopment =
        environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() ?: false

    val allTables = arrayOf(
        UserTable,
        DeviceTable,
        MfaCodeTable,
        RefreshTokenTable,
        LocationsTable,
        FieldTable,
        FieldImagesTable,
        FieldAdminsTable,
        DiscountsTable,
        UserMatchDiscountsTable,
        MatchTable,
        MatchDiscountsTable,
        MatchPlayersTable,
        MatchResultsTable,
        PasswordResetTokensTable,
        LoginAttemptTable,
        PendingRegistrationTable,
        MatchPlayerPaymentsTable,
        UserPaymentProfileTable,
        StripeWebhookEventsTable
    )

    transaction(database) {
        if (isDevelopment) {
            addLogger(StdOutSqlLogger)
        }

        val migrationStatements =
            MigrationUtils.statementsRequiredForDatabaseMigration(*allTables)

        if (migrationStatements.isNotEmpty()) {
            log.info("Pending DB migration statements:")

            migrationStatements.forEach { statement ->
                log.info(statement)
            }

            val dangerousStatements = migrationStatements.filter {
                val normalized = it.trim().uppercase()
                normalized.startsWith("DROP ")
            }

            if (dangerousStatements.isNotEmpty()) {
                error("Dangerous migration statements detected. Refusing to run automatically.")
            }

            migrationStatements.forEach { statement ->
                exec(statement)
            }
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction {
            block()
        }
    }