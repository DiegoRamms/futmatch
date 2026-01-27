package com.devapplab.data.database.executor

import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedDbExecutor(
    private val config: ApplicationConfig
) : DbExecutor {
    override suspend fun <T> tx(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            if (config.propertyOrNull("ktor.development")?.getString()?.toBoolean() == true) {
                addLogger(StdOutSqlLogger)
            }
            block()
        }
}