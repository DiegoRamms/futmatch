package com.devapplab.config

import com.devapplab.data.database.field.FieldAdminsTable
import com.devapplab.data.database.refresh_token.RefreshTokenTable
import com.devapplab.data.database.user.UserTable
import data.database.device.DeviceTable
import data.database.field.FieldImagesTable
import data.database.field.FieldTable
import data.database.match.MatchTable
import data.database.mfa.MfaCodeTable
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

    transaction(database){
        SchemaUtils.create(UserTable)
        SchemaUtils.create(DeviceTable)
        SchemaUtils.create(MfaCodeTable)
        SchemaUtils.create(RefreshTokenTable)
        SchemaUtils.create(FieldTable)
        SchemaUtils.create(FieldImagesTable)
        SchemaUtils.create(FieldAdminsTable)
        SchemaUtils.create(MatchTable)

        addLogger(StdOutSqlLogger)
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }