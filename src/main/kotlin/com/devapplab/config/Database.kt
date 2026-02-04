package com.devapplab.config

import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.login_attempt.LoginAttemptTable
import com.devapplab.data.database.pending_registrations.PendingRegistrationTable
import com.devapplab.data.database.password_reset.PasswordResetTokensTable
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.data.database.user.UserTable
import com.devapplab.data.database.device.DeviceTable
import com.devapplab.data.database.discount.DiscountsTable
import com.devapplab.data.database.discount.UserMatchDiscountsTable
import com.devapplab.data.database.field.FieldImagesTable
import com.devapplab.data.database.field.FieldTable
import com.devapplab.data.database.location.LocationsTable
import com.devapplab.data.database.match.MatchDiscountsTable
import com.devapplab.data.database.match.MatchPlayersTable
import com.devapplab.data.database.match.MatchResultsTable
import com.devapplab.data.database.match.MatchTable
import com.devapplab.data.database.mfa.MfaCodeTable
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


fun Application.configureDatabase() {
    val database = Database.connect(
        url = environment.config.propertyOrNull("database.url")?.getString() ?: "",
        driver = environment.config.propertyOrNull("database.driver")?.getString() ?: "",
        user = environment.config.propertyOrNull("database.user")?.getString() ?: "",
        password = environment.config.propertyOrNull("database.password")?.getString() ?: ""
    )

    // Use ktor.development flag to conditionally add logger
    val isDevelopment = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() ?: false

    transaction(database) {
        SchemaUtils.create(
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
        )

        if (isDevelopment) {
            addLogger(StdOutSqlLogger)
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }