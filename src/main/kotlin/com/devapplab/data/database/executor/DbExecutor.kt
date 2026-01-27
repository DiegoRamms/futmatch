package com.devapplab.data.database.executor

interface DbExecutor {
    suspend fun <T> tx(block: () -> T): T
}