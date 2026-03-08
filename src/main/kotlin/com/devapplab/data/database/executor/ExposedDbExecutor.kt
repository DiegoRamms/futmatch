package com.devapplab.data.database.executor

import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class ExposedDbExecutor(
    private val config: ApplicationConfig
) : DbExecutor {

    override suspend fun <T> tx(block: () -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                if (config.propertyOrNull("ktor.development")?.getString()?.toBoolean() == true) {
                    addLogger(StdOutSqlLogger)
                }
                block()
            }
        }
}