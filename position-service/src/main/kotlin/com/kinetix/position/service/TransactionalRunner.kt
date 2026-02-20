package com.kinetix.position.service

import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface TransactionalRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class ExposedTransactionalRunner : TransactionalRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        newSuspendedTransaction { block() }
}
