package com.devapplab.config

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.config.MatchPricingConfigTable
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
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory

fun Application.configureDatabase() {
    val config = environment.config
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.requiredProperty("database.url")
        driverClassName = config.requiredProperty("database.driver")
        username = config.requiredProperty("database.user")
        password = config.requiredProperty("database.password")
        maximumPoolSize = config.intProperty("database.pool.maximum_size", default = 5)
        minimumIdle = config.intProperty("database.pool.minimum_idle", default = 1)
        connectionTimeout = config.longProperty("database.pool.connection_timeout_ms", default = 5_000)
        validationTimeout = config.longProperty("database.pool.validation_timeout_ms", default = 3_000)
        idleTimeout = config.longProperty("database.pool.idle_timeout_ms", default = 600_000)
        keepaliveTime = config.longProperty("database.pool.keepalive_time_ms", default = 120_000)
        maxLifetime = config.longProperty("database.pool.max_lifetime_ms", default = 1_800_000)
        poolName = "futmatch-postgres"
        metricsTrackerFactory = MicrometerMetricsTrackerFactory(prometheusMeterRegistry())
        addDataSourceProperty("tcpKeepAlive", "true")
    })
    val database = Database.connect(dataSource)

    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }

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
        NotificationTable,
        MatchPricingConfigTable
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

        backfillCompletedMatchAttendance()
    }
}

private fun ApplicationConfig.requiredProperty(path: String): String =
    propertyOrNull(path)?.getString()?.takeIf(String::isNotBlank)
        ?: error("Missing required database configuration: $path")

private fun ApplicationConfig.intProperty(path: String, default: Int): Int =
    propertyOrNull(path)?.getString()?.toIntOrNull() ?: default

private fun ApplicationConfig.longProperty(path: String, default: Long): Long =
    propertyOrNull(path)?.getString()?.toLongOrNull() ?: default

private val databaseMigrationLogger = LoggerFactory.getLogger("DatabaseMigration")

private fun JdbcTransaction.renameFieldPriceColumnIfNeeded() {
    val hasLegacyColumn = hasColumn(tableName = "fields", columnName = "price_per_player")
    val hasRenamedColumn = hasColumn(tableName = "fields", columnName = "field_cost")

    if (!hasLegacyColumn || hasRenamedColumn) return

    databaseMigrationLogger.info("Renaming fields.price_per_player to fields.field_cost")
    exec("ALTER TABLE fields RENAME COLUMN price_per_player TO field_cost")
}

private fun JdbcTransaction.backfillCompletedMatchAttendance() {
    exec(
        """
        UPDATE match_players
        SET attendance_status = 'PRESENT'
        WHERE attendance_status IS NULL
          AND status IN ('RESERVED', 'JOINED')
          AND match_id IN (SELECT id FROM matches WHERE status = 'COMPLETED')
        """.trimIndent()
    )
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
