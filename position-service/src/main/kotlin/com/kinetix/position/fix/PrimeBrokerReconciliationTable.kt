package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object PrimeBrokerReconciliationTable : Table("prime_broker_reconciliation") {
    val id = varchar("id", 255)
    val reconciliationDate = varchar("reconciliation_date", 10)
    val bookId = varchar("book_id", 255)
    val status = varchar("status", 20)
    val totalPositions = integer("total_positions")
    val matchedCount = integer("matched_count")
    val breakCount = integer("break_count")
    val breaks = text("breaks")   // JSON stored as text via kotlinx.serialization
    val reconciledAt = timestampWithTimeZone("reconciled_at")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
