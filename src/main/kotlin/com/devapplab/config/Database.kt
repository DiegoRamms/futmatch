package com.devapplab.config

import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.device.DesktopDeviceTable
import com.devapplab.data.database.device.DesktopEnrollmentRequestTable
import com.devapplab.data.database.device.DesktopRequestNonceTable
import com.devapplab.data.database.config.MatchPricingConfigTable
import com.devapplab.data.database.cleanup.ProfileImageCleanupJobsTable
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
import com.devapplab.model.PiiCryptoConfig
import com.devapplab.service.pii.PiiCrypto
import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
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
        DesktopDeviceTable,
        DesktopEnrollmentRequestTable,
        DesktopRequestNonceTable,
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
        MatchPricingConfigTable,
        ProfileImageCleanupJobsTable
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
        migrateLegacyPii(config)
    }
}

private fun JdbcTransaction.migrateLegacyPii(config: ApplicationConfig) {
    val lockAcquired = exec("SELECT pg_try_advisory_xact_lock(842019477)") { resultSet -> resultSet.next() && resultSet.getBoolean(1) }
    if (lockAcquired != true) {
        databaseMigrationLogger.info("Skipping legacy PII migration because another instance holds the migration lock.")
        return
    }

    val piiCrypto = PiiCrypto(
        PiiCryptoConfig(
            encryptionKeyBase64 = config.property("pii.encryptionKey").getString(),
            lookupPepperBase64 = config.property("pii.lookupPepper").getString(),
            keyVersion = config.propertyOrNull("pii.keyVersion")?.getString()?.trim().orEmpty().ifBlank { "v1" },
            previousEncryptionKeys = config.propertyOrNull("pii.previousEncryptionKeys")?.getString()
                .orEmpty()
                .split(',')
                .mapNotNull { entry -> entry.trim().takeIf(String::isNotBlank)?.split(':', limit = 2) }
                .associate { (version, key) -> version.trim() to key.trim() }
        )
    )

    val users = UserTable.selectAll().where { UserTable.email.isNotNull() }.forUpdate().toList()
    users.forEach { row ->
        val email = requireNotNull(row[UserTable.email])
        val phone = requireNotNull(row[UserTable.phone])
        UserTable.update({ UserTable.id eq row[UserTable.id] }) {
            it[UserTable.email] = null
            it[UserTable.emailCiphertext] = piiCrypto.encrypt(piiCrypto.normalizeEmail(email))
            it[UserTable.emailLookup] = piiCrypto.emailLookup(email)
            it[UserTable.phone] = null
            it[UserTable.phoneCiphertext] = piiCrypto.encrypt(piiCrypto.normalizePhone(phone))
            it[UserTable.phoneLookup] = piiCrypto.phoneLookup(phone)
            it[UserTable.piiKeyVersion] = piiCrypto.keyVersion
        }
    }

    val pendingRegistrations = PendingRegistrationTable.selectAll()
        .where { PendingRegistrationTable.email.isNotNull() }
        .forUpdate()
        .toList()
    pendingRegistrations.forEach { row ->
        val email = requireNotNull(row[PendingRegistrationTable.email])
        val phone = requireNotNull(row[PendingRegistrationTable.phone])
        PendingRegistrationTable.update({ PendingRegistrationTable.id eq row[PendingRegistrationTable.id] }) {
            it[PendingRegistrationTable.email] = null
            it[PendingRegistrationTable.emailCiphertext] = piiCrypto.encrypt(piiCrypto.normalizeEmail(email))
            it[PendingRegistrationTable.emailLookup] = piiCrypto.emailLookup(email)
            it[PendingRegistrationTable.phone] = null
            it[PendingRegistrationTable.phoneCiphertext] = piiCrypto.encrypt(piiCrypto.normalizePhone(phone))
            it[PendingRegistrationTable.phoneLookup] = piiCrypto.phoneLookup(phone)
            it[PendingRegistrationTable.piiKeyVersion] = piiCrypto.keyVersion
        }
    }

    LoginAttemptTable.selectAll().where { LoginAttemptTable.email.isNotNull() }.forUpdate().forEach { row ->
        val email = requireNotNull(row[LoginAttemptTable.email])
        LoginAttemptTable.update({ LoginAttemptTable.id eq row[LoginAttemptTable.id] }) {
            it[LoginAttemptTable.email] = null
            it[LoginAttemptTable.emailLookup] = piiCrypto.emailLookup(email)
        }
    }
    PasswordResetVerifyAttemptTable.selectAll().where { PasswordResetVerifyAttemptTable.email.isNotNull() }.forUpdate().forEach { row ->
        val email = requireNotNull(row[PasswordResetVerifyAttemptTable.email])
        PasswordResetVerifyAttemptTable.update({ PasswordResetVerifyAttemptTable.id eq row[PasswordResetVerifyAttemptTable.id] }) {
            it[PasswordResetVerifyAttemptTable.email] = null
            it[PasswordResetVerifyAttemptTable.emailLookup] = piiCrypto.emailLookup(email)
        }
    }
    RegistrationVerifyAttemptTable.selectAll().where { RegistrationVerifyAttemptTable.email.isNotNull() }.forUpdate().forEach { row ->
        val email = requireNotNull(row[RegistrationVerifyAttemptTable.email])
        RegistrationVerifyAttemptTable.update({ RegistrationVerifyAttemptTable.id eq row[RegistrationVerifyAttemptTable.id] }) {
            it[RegistrationVerifyAttemptTable.email] = null
            it[RegistrationVerifyAttemptTable.emailLookup] = piiCrypto.emailLookup(email)
        }
    }

    if (users.isNotEmpty() || pendingRegistrations.isNotEmpty()) {
        databaseMigrationLogger.info("Migrated {} users and {} pending registrations to encrypted PII.", users.size, pendingRegistrations.size)
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
