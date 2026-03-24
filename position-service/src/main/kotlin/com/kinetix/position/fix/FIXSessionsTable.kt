package com.kinetix.position.fix

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object FIXSessionsTable : Table("fix_sessions") {
    val sessionId = varchar("session_id", 255)
    val counterparty = varchar("counterparty", 255)
    val status = varchar("status", 30)
    val lastMessageAt = timestampWithTimeZone("last_message_at").nullable()
    val inboundSeqNum = integer("inbound_seq_num")
    val outboundSeqNum = integer("outbound_seq_num")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(sessionId)
}
