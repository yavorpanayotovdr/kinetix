package com.kinetix.position.service

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface TransactionalRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class ExposedTransactionalRunner(private val db: Database? = null) : TransactionalRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        newSuspendedTransaction(db = db) { block() }
}
