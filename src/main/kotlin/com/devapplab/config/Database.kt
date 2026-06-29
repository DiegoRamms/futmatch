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
import com.devapplab.data.database.match.MatchPlayerGoalsTable
import com.devapplab.data.database.match.MatchRefundFailuresTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchResultsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.match.PublicMatchesVersionTable
import com.devapplab.data.database.mfa.LoginMfaChallengeTable
import com.devapplab.data.database.mfa.MfaCodeTable
import com.devapplab.data.database.mfa.LoginMfaVerifyAttemptTable
import com.devapplab.data.database.notification.NotificationTable
import com.devapplab.data.database.password_reset.PasswordResetTokensTable
import com.devapplab.data.database.password_reset.PasswordResetVerifyAttemptTable
import com.devapplab.data.database.payments.MatchPlayerPaymentsTable
import com.devapplab.data.database.payments.StripeWebhookEventsTable
import com.devapplab.data.database.pending_registrations.PendingRegistrationTable
import com.devapplab.data.database.pending_registrations.RegistrationVerifyAttemptTable
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
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory

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
        LoginMfaChallengeTable,
        LoginMfaVerifyAttemptTable,
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
        MatchPlayerGoalsTable,
        MatchRefundFailuresTable,
        MatchResultsTable,
        PublicMatchesVersionTable,
        PasswordResetTokensTable,
        PasswordResetVerifyAttemptTable,
        LoginAttemptTable,
        PendingRegistrationTable,
        RegistrationVerifyAttemptTable,
        MatchPlayerPaymentsTable,
        UserPaymentProfileTable,
        StripeWebhookEventsTable,
        NotificationTable
    )

    transaction(database) {
        if (isDevelopment) {
            addLogger(StdOutSqlLogger)
        }

        renameFieldPriceColumnIfNeeded()

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

private val databaseMigrationLogger = LoggerFactory.getLogger("DatabaseMigration")

private fun JdbcTransaction.renameFieldPriceColumnIfNeeded() {
    val hasLegacyColumn = hasColumn(tableName = "fields", columnName = "price_per_player")
    val hasRenamedColumn = hasColumn(tableName = "fields", columnName = "field_cost")

    if (!hasLegacyColumn || hasRenamedColumn) return

    databaseMigrationLogger.info("Renaming fields.price_per_player to fields.field_cost")
    exec("ALTER TABLE fields RENAME COLUMN price_per_player TO field_cost")
}

private fun JdbcTransaction.hasColumn(tableName: String, columnName: String): Boolean {
    return exec(
        """
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE LOWER(TABLE_NAME) = LOWER('$tableName')
          AND LOWER(COLUMN_NAME) = LOWER('$columnName')
        LIMIT 1
        """.trimIndent()
    ) { resultSet ->
        resultSet.next()
    } ?: false
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction {
            block()
        }
    }
