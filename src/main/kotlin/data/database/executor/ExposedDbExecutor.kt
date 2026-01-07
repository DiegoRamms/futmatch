package com.devapplab.data.database.executor

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedDbExecutor() : DbExecutor {
    override suspend fun <T> tx(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}